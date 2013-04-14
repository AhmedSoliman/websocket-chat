package models

import play.api.libs.iteratee._
import play.api.libs.json._
import akka.actor._
import play.api.libs.concurrent.Akka
import play.api.Play.current
import play.Logger

object UserActorProtocol {
  case class CreateUser(username: String, email: String)
  case class UserWSPair(in: Iteratee[JsValue, _], out: Enumerator[JsValue])
  case class KickUser(username: String)
  case object Kicked
  case object Connect
  case class Connected(enumerator: Enumerator[JsValue])
  case class CannotConnect(msg: String)
  case class UserNotOnline(username: String)
  case class SendRoomMessage(who: User, room: String, body: String)
  case class SendDirectMessage(who: User, body: String)
  case class NotifyRoomJoin(room: String, who: User, isJoining: Boolean)
}

object RoomActorProtocol {
  case class JoinRoom(room: String, user: User)
  case class CreateRoom(room: String, owner: User)
  case class UserAlreadyInRoom(room: String)
  case object NoSuchRoom
  case class RoomAlreadyExists(room: String)
  case class SendMembersList(room: String, to: User)
  case class SendRoomsList(to: User)
  case class RoomsList(rooms: Seq[Room])
  case class RoomMembersList(room: String, members: Set[User])
  case class LeaveRoom(username: User)
  case class Talk(who: User, room: String, body: String)
}

object Protocol {
  import UserActorProtocol._
  import RoomActorProtocol._

  lazy val roomManager: ActorRef = Akka.system.actorFor("akka://application/user/rooms")
  lazy val userManager: ActorRef = Akka.system.actorFor("akka://application/user/users")

  def formatRoomMembersList(message: RoomMembersList): JsValue = 
    Json.obj(
        "kind" -> "roomMembers",
        "object" -> Json.obj(
            "roomname" -> message.room,
            "roomList" -> message.members.map(u => Json.obj("username" -> u.username, "email" -> u.email))))
            
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
      "object" -> Json.obj("username" -> message.who.username,
        "room" -> message.room,
        "body" -> message.body))

  def createUserIteratee(user: User): Iteratee[JsValue, _] =
    Iteratee.foreach[JsValue] { event =>
      Logger.info(s"recieved event: $event")
      (event \ "kind").as[String] match { // handle the general events
        case "join" =>
          roomManager ! JoinRoom((event \ "object" \ "room").as[String], user)
        case "createRoom" =>
          roomManager ! CreateRoom((event \ "object" \ "roomname").as[String], user)
        case "getRoomList" => {
        	roomManager ! SendMembersList((event \ "object" \ "roomname").as[String], user)
        }
        case "sendMessage" => {
          val room = (event \ "object" \ "room").as[String]
          val body = (event \ "object" \ "body").as[String]
          roomManager ! Talk(user, room, body)
        }
      }
    }.mapDone { _ =>
        user.actor ! KickUser(user.username)
        //TODO: leave all rooms I joined
    }
}