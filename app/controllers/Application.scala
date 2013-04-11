package controllers

import play.api._
import play.api.mvc._

import play.api.libs.json._
import play.api.libs.iteratee._

import models._

import akka.actor._
import scala.concurrent.duration._

object Application extends Controller {

  /**
   * Just display the home page.
   */
  def index = Action { implicit request =>
    Ok(views.html.index(Lobby.rooms))
  }
  
  /**
   * Display the chat room page.
   */
  def chatRoom(room: Option[String], username: Option[String]) = Action { implicit request =>
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
  def ws(room: String, username: String, email: String) = WebSocket.async[JsValue] {
    implicit request =>
      Lobby.createRoom(room).join(username, email)
  }

}
