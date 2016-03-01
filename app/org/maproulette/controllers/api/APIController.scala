package org.maproulette.controllers.api

import controllers.Application._
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}

/**
  * @author cuthbertm
  */
object APIController extends Controller {
  def invalidAPIPath(path:String) = Action {
    val message = s"Invalid Path [$path] for API"
    BadRequest(Json.obj("status" -> "KO", "message" -> message))
  }
}
