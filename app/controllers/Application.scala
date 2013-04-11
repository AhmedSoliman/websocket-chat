package controllers

import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.iteratee._
import akka.actor._
import scala.concurrent.duration._
import scala.concurrent._
import akka.pattern.ask
import play.api.libs.concurrent.Execution.Implicits._
import models.UserActorProtocol.CreateUser
import play.api.libs.concurrent.Akka
import play.api.Play.current
import actors.UserManager
import akka.util.Timeout
import play.api.data._
import play.api.data.Forms._
import models.UserActorProtocol
import actors.RoomManager

object Application extends Controller {
  private[this] implicit val timeout = Timeout(1 seconds)
  val userManager: ActorRef = Akka.system.actorOf(Props[UserManager], name = "users")
  val roomsManager: ActorRef = Akka.system.actorOf(Props[RoomManager], name = "rooms")
  /**
   * Just display the home page.
   */
  def index = Action { implicit request =>
    Ok(views.html.index())
  }

  def login = Action { implicit request =>
    //TODO: validation

    val loginForm = Form(
      tuple(
        "username" -> text,
        "email" -> text))
    val (username, email) = loginForm.bindFromRequest.get
    Created.withSession("username" -> username, "email" -> email)
  }

  def logout = Action { implicit request =>
    //TODO: kick me from all rooms and destroy my own actor (terminate the user enumerator)
    Ok.withNewSession
  }

  /**
   * Display the chat room page.
   */
  def chatRoom(room: Option[String]) = Action { implicit request =>
    val username = session.get("username")
    username.filterNot(_.isEmpty).map { username =>
      room.filterNot(_.isEmpty).map { room =>
        Ok(views.html.chatRoom(room, username))
      }.getOrElse {
        Redirect(routes.Application.index).flashing(
          "error" -> "Please choose a room name.")
      }
    }.getOrElse {
      Redirect(routes.Application.index).flashing(
        "error" -> "Please choose a valid username.")
    }
  }

  /**
   * Handles the chat websocket.
   */
  def ws = WebSocket.async[JsValue] {
    implicit request =>
      //check if logged in
      request.session.get("username").map { username =>
        Logger.info(s"WS CONNECTED <$username>")
        (userManager ? CreateUser(username, request.session("email"))).map {
          case UserActorProtocol.UserWSPair(in, out) =>
            (in, out)
          case e => 
            throw new Exception("WTF!")
        }

      }.getOrElse {
        val in = Done[JsValue, Unit]((), Input.EOF)
        // Send an error and close the socket
        val errOut = Enumerator[JsValue](JsObject(Seq("error" -> JsString("Forbidden, you are not logged in")))).andThen(Enumerator.enumInput(Input.EOF))
        future((in, errOut))
      }
  }

}
