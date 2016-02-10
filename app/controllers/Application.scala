package controllers

import play.api.libs.json.Json
import play.api.mvc._

object Application extends Controller {

  def index = Action {
    Ok(views.html.index("Title"))
  }

  def invalidAPIPath(path:String) = Action {
    val message = s"Invalid Path [$path] for API"
    BadRequest(Json.obj("status" -> "KO", "message" -> message))
  }
}
