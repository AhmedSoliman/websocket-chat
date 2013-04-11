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
import scala.collection.mutable.{ Map => MutableMap }

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
class Room(val name: String) { // change the class name
  lazy val roomActor = Akka.system.actorOf(Props[RoomActor], name = name)
  private[this] implicit val timeout = Timeout(1 second)

  def join(username: String, email: String): Future[(Iteratee[JsValue, _], Enumerator[JsValue])] = {
    lazy val (userEnumerator, userChannel) = Concurrent.broadcast[JsValue]
    (roomActor ? Join(username, email, userChannel)).map {
      case Connected() =>
        // Create an Iteratee to consume the feed
        val iteratee = Iteratee.foreach[JsValue] { event =>
          println(s"msg rcv: $event")
          val eventObject = (event \ "object")
          (event \ "kind").as[String] match { // handle the general events
            case "join" => {
              println(s"kind: join, object: $eventObject")
              roomActor ! Join(username, email, userChannel)
            }
            case "createRoom" => {
              println(s"knid: createRoom, object: $eventObject")
              Lobby.createRoom((event \ "object" \ "roomname").as[String]) //TODO: handle public?
            }
            case "getRoomList" => {
              println(s"knid: getRoomList, object: $eventObject")
              val responseObject = JsObject(Seq("roomList" -> Json.toJson(Lobby.rooms)))
              val msg = JsObject(
                Seq(
                  "kind" -> JsString("roomList"),
                  "object" -> responseObject))
              userChannel push msg
            }
            case roomEventKind => roomActor ! RoomEvent(username, roomEventKind, (event \ "object"))
          }
        }.mapDone { _ =>
          roomActor ! Quit(username)
        }
        (iteratee, userEnumerator) //return value

      case CannotConnect(error) =>
        // Connection error
        // A finished Iteratee sending EOF
        val iteratee = Done[JsValue, Unit]((), Input.EOF)
        // Send an error and close the socket
        val errorEnumerator = Enumerator[JsValue](JsObject(Seq("error" -> JsString(error)))).andThen(Enumerator.enumInput(Input.EOF))
        (iteratee, errorEnumerator) //return value
    }
  }
  def members(): Future[MutableMap[String, String]] =
    (roomActor ? GetMembers()).map {
      case Members(members) => members
    }
}

object Lobby { // Merge into the RoomClass
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

  val members: MutableMap[String, String] = MutableMap.empty
  val usersChannels: MutableMap[String, Concurrent.Channel[JsValue]] = MutableMap.empty

  def receive = {
    case Join(username, email, userChannel) => {
      if (members.contains(username)) {
        sender ! CannotConnect("This username is already used")
      } else {
        members put (username, email)
        usersChannels put (username, userChannel)
        sender ! Connected()
        self ! NotifyJoin(username, email)
      }
    }

    case NotifyJoin(username, email) => {
      val jsonObject =
        JsObject(Seq(
          "room" -> JsString("room1"), //TODO: get the roomname 
          "user" -> JsObject(Seq(
            "username" -> JsString(username),
            "email" -> JsString(email)))))
      notifyAll("notifyJoin", jsonObject)
    }
    case RoomEvent(username, kind, eventObject) => // handle room events
      kind match {
        case "sendMessage" => {
          println(s"knid: sendMessage, object: $eventObject")
          val jsonObject =
            JsObject(Seq(
              "username" -> JsString(username),
              "room" -> eventObject \ "room",
              "body" -> eventObject \ "body"))
          notifyAll("message", jsonObject)
        }
        case "getRoomMembers" => {
          println(s"knid: getRoomMembers, object: $eventObject")
          val jsonObject = JsObject(Seq(
            "roomname" -> JsString("room1"), //TODO: get the roomname
            "membersList" -> Json.toJson(members.foldLeft(
              List[Map[String, String]]())(
                (acc, elem) => acc :+ Map("username" -> elem._1, "email" -> elem._2)))))
          notifyAll("roomMembers", jsonObject)
        }
        case _ => {
          println(s"knid: UNKNOWN, object: $eventObject")
        }
      }

    case Quit(username) => {
      members remove username
      val jsonObject = Json.toJson(Map(
        "room" -> "blablabla2", //TODO: get the roomname
        "username" -> username))
      notifyAll("notifyLeave", jsonObject)
    }
    case GetMembers() =>
      sender ! Members(members)
  }
  private[this] def notifyAll(kind: String, responseObject: JsValue) { //TODO: generalize
    val msg = JsObject(
      Seq(
        "kind" -> JsString(kind),
        "object" -> responseObject))
    println(s"send -> : $msg")
    usersChannels foreach { case (k, channel) => channel push msg }
  }
}

case class RoomEvent(username: String, kind: String, eventObject: JsValue)
case class Join(username: String, email: String, userChannel: Concurrent.Channel[JsValue])
case class Quit(username: String)
case class NotifyJoin(username: String, email: String)
case class GetMembers()
case class Members(members: MutableMap[String, String])
case class Connected()
case class CannotConnect(msg: String)
