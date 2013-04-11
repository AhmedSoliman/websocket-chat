package models
import akka.actor._

case class User(username: String, email: String, actor: ActorRef)

case class Room(name: String, owner: User, actor: ActorRef)
