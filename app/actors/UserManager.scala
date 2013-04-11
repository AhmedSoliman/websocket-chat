package actors

import akka.actor._
import scala.concurrent.duration._
import play.api._
import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent._
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent.Future
import play.api.Play.current
import models.User
import models.Protocol
import play.api.libs.concurrent.Execution.Implicits._
import models.UserActorProtocol._

class UserManager extends Actor with ActorLogging {
  private[this] implicit val timeout = Timeout(1 second)
  //who is now online
  private[this] var onlineUsers: Map[String, User] = Map.empty

  def receive = {
    case CreateUser(username, email) =>
      val user = if (onlineUsers contains username) {
        onlineUsers(username)
      } else {
        //check if there is an actor
        val u = User(username, email, context.actorOf(Props(new UserActor(username)), name = s"user:$username"))
        onlineUsers += (username -> u)
        u
      }
      //new iteratee for every connection even to the same user
      val iteratee = Protocol.createUserIteratee(user)
      //return enumerator from the user actor
      (user.actor ? Connect).map { case Connected(enumerator) => sender ! (iteratee -> enumerator) }
    case e @ KickUser(username: String) =>
      if (onlineUsers contains username) {
        val user = onlineUsers(username)
        val r = user.actor ? e //sending the same KickUser to the user actor
        r.map { 
          case Kicked => 
          	onlineUsers -= (username)
          	context.stop(user.actor) //died
          }
      } else {
        sender ! UserNotOnline(username)
      }
      
  }
}

class UserActor(username: String) extends Actor with ActorLogging {
  private[this] val (enumerator, channel) = Concurrent.broadcast[JsValue]
  def receive = {
    case Connect =>
      sender ! Connected(enumerator)
      
    case message: SendRoomMessage => 
      channel.push(Protocol.formatRoomMessage(message))
      
    case KickUser(_) =>
      //broadcast to all rooms (actors) to kick this user out
      channel.push(Protocol.formatKickMessage(username))
      channel.end()
      // return
      sender ! Kicked
      
    case e @ NotifyRoomJoin(room, who, joining) =>
      //tell him that WHO arrived/left
      channel.push(Protocol.formatJoinMessage(e))
  }
}