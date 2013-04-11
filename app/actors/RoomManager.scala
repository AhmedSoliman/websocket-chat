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
import models.{ User, Room }
import models.Protocol
import play.api.libs.concurrent.Execution.Implicits._
import models.RoomActorProtocol._
import models.UserActorProtocol

class RoomManager extends Actor with ActorLogging {
  private[this] implicit val timeout = Timeout(1 seconds)
  private[this] var rooms: Map[String, Room] = Map.empty
  def receive = {
    case CreateRoom(room, owner) =>
      if (rooms contains room) {
        sender ! RoomAlreadyExists
      } else {
        val roomObj = Room(room, owner, context.actorOf(Props(new RoomActor(room, owner)), name = s"room:$room"))
        rooms += (room -> roomObj)
        sender ! RoomCreated
      }

    case e @ JoinRoom(room, user) =>
      rooms.get(room).map { room =>
        (room.actor ? e).map(sender ! _)
      }.getOrElse {
        sender ! NoSuchRoom
      }
    case e @ Talk(who, room, body) =>
      if (rooms contains room) {
        rooms(room).actor ! e
        // needs thinking, should we return or not?!
      } else {
        sender ! NoSuchRoom
      }
  }
}

class RoomActor(name: String, ownerUsername: String) extends Actor with ActorLogging {
  private[this] implicit val timeout = Timeout(1 seconds)
  private[this] var members: Set[User] = Set.empty

  private def sendToAll(message: AnyRef): Unit = {
    members.foreach(_.actor ! message)
  }

  def receive = {
    case JoinRoom(_, user) =>
      if (members contains user) {
        user.actor ! UserAlreadyInRoom(name)
      } else {
        sendToAll(UserActorProtocol.NotifyRoomJoin(name, user, true)) //notify everybody
        user.actor ! RoomMembersList(name, members) //return user list
      }
    case Talk(who, _, body) =>
    	sendToAll(UserActorProtocol.SendRoomMessage(who, name, body))
    case SendMembersList(room, user) => ???
  }
}