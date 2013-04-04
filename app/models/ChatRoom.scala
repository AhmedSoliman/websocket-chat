package models

import akka.actor._
import scala.concurrent.duration._
import play.api._
import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent._
import akka.util.Timeout
import akka.pattern.ask
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

//object Robot {
//
//  def apply(chatRoom: ActorRef) {
//
//    // Create an Iteratee that logs all messages to the console.
//    val loggerIteratee = Iteratee.foreach[JsValue](event => Logger("robot").info(event.toString))
//
//    implicit val timeout = Timeout(1 second)
//    // Make the robot join the room
//    chatRoom ? (Join("Robot")) map {
//      case Connected(robotChannel) =>
//        // Apply this Enumerator on the logger.
//        robotChannel |>> loggerIteratee
//    }
//
//    // Make the robot talk every 30 seconds
//    Akka.system.scheduler.schedule(
//      30 seconds,
//      30 seconds,
//      chatRoom,
//      Talk("Robot", "I'm still alive"))
//  }
//
//}
class Room(val name: String) {
  lazy val roomActor = Akka.system.actorOf(Props[RoomActor], name = name)
  private[this] implicit val timeout = Timeout(1 second)

  def join(username: String): Future[(Iteratee[JsValue, _], Enumerator[JsValue])] = {
    (roomActor ? Join(username)).map {
      case Connected(enumerator) =>
        // Create an Iteratee to consume the feed
        val iteratee = Iteratee.foreach[JsValue] { event =>
          roomActor ! Talk(username, (event \ "text").as[String])
        }.mapDone { _ =>
          roomActor ! Quit(username)
        }
        (iteratee, enumerator) //return value

      case CannotConnect(error) =>
        // Connection error
        // A finished Iteratee sending EOF
        val iteratee = Done[JsValue, Unit]((), Input.EOF)
        // Send an error and close the socket
        val enumerator = Enumerator[JsValue](JsObject(Seq("error" -> JsString(error)))).andThen(Enumerator.enumInput(Input.EOF))
        (iteratee, enumerator) //return value
    }
  }
  def members(): Future[Set[String]] = 
    (roomActor ? GetMembers()).map {
    case Members(members) => members
  }
}

object Lobby {
  import scala.collection.mutable
  private val roomsMap: mutable.Map[String, Room] = mutable.Map.empty
  
  def rooms(): Seq[String] = roomsMap.keys.toSeq
  def createRoom(name: String): Room =
    synchronized {
    	if (roomsMap.contains(name))
    		roomsMap(name)
		else {
			val r = new Room(name)
			roomsMap.put(name, r)
			r
		}
    }
}

class RoomActor extends Actor with ActorLogging {
  var members: Set[String] = Set.empty
  lazy val (chatEnumerator, chatChannel) = Concurrent.broadcast[JsValue]

  def receive = {
    case Join(username) => {
      if (members.contains(username)) {
        sender ! CannotConnect("This username is already used")
      } else {
        members += username
        sender ! Connected(chatEnumerator)
        self ! NotifyJoin(username)
      }
    }

    case NotifyJoin(username) => {
      notifyAll("join", username, "has entered the room")
    }

    case Talk(username, text) => {
      notifyAll("talk", username, text)
    }

    case Quit(username) => {
      members = members - username
      notifyAll("quit", username, "has left the room")
    }
    case GetMembers() => 
      sender ! Members(members)
  }
  private[this] def notifyAll(kind: String, user: String, text: String) {
    val msg = JsObject(
      Seq(
        "kind" -> JsString(kind),
        "user" -> JsString(user),
        "message" -> JsString(text),
        "members" -> JsArray(
          members.toList.map(JsString))
        ))
    chatChannel.push(msg)
  }  
}

case class Join(username: String)
case class Quit(username: String)
case class Talk(source: String, text: String)
case class NotifyJoin(username: String)
case class GetMembers()
case class Members(members: Set[String])
case class Connected(enumerator: Enumerator[JsValue])
case class CannotConnect(msg: String)
