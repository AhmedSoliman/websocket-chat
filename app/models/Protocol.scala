package models

import play.api.libs.iteratee._
import play.api.libs.json._
import akka.actor._

object UserActorProtocol {
  case class CreateUser(username: String, email: String)
  case class KickUser(username: String)
  case object Kicked
  case object Connect
  case class Connected(enumerator: Enumerator[JsValue])
  case class CannotConnect(msg: String)
  case class UserNotOnline(username: String)
  case class SendRoomMessage(who: String, room: String, body: String)
  case class SendDirectMessage(who: String, body: String)
  case class NotifyRoomJoin(room: String, who: User, isJoining: Boolean)
}

object RoomActorProtocol {
  case class JoinRoom(room: String, user: User)
  case class CreateRoom(room: String, owner: String)
  case class UserAlreadyInRoom(room: String)
  case object NoSuchRoom
  case object RoomAlreadyExists
  case object RoomCreated
  case class SendMembersList(room: String, to: User)
  case object SendRoomsList
  case class RoomsList(rooms: Seq[Room])
  case class RoomMembersList(room: String, members: Set[User])
  case class LeaveRoom(username: String)
  case class Talk(who: String, room: String, body: String)
}

object Protocol {
  import UserActorProtocol._
  import RoomActorProtocol._

  lazy val roomManager: ActorRef = ???
  lazy val userManager: ActorRef = ???

  def formatJoinMessage(message: NotifyRoomJoin): JsValue =
    Json.obj(
      "kind" -> (if (message.isJoining) "notifyJoin" else "notifyLeave"),
      "object" -> Json.obj("room" -> message.room,
        "user" -> Json.obj("username" -> message.who.username, "email" -> message.who.email) //TODO: implement an automatic writer of User object => Json
        ))

  def formatKickMessage(username: String): JsValue =
    Json.obj("kind" -> "killed-signal")

  def formatRoomMessage(message: SendRoomMessage): JsValue = //TODO
    Json.obj(
      "kind" -> "message",
      "object" -> Json.obj("username" -> message.who,
        "room" -> message.room,
        "body" -> message.body))

  def createUserIteratee(user: User): Iteratee[JsValue, _] =
    Iteratee.foreach[JsValue] { event =>
      //(event \ "object")
      (event \ "kind").as[String] match { // handle the general events
        case "join" => 
          roomManager ! JoinRoom((event \ "object" \ "room").as[String], user)
        case "createRoom" =>
          roomManager ! CreateRoom((event \ "object" \ "roomname").as[String], user.username)
        case "getRoomList" =>
          roomManager ! SendMembersList((event \ "object" \ "roomname").as[String], user)
      }
    }.mapDone { _ =>
        user.actor ! KickUser(user.username)
        //TODO: leave all rooms I joined
    }
}