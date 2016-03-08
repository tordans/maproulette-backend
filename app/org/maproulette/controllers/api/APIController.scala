package org.maproulette.controllers.api

import javax.inject.Inject
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}

/**
  * @author cuthbertm
  */
class APIController @Inject() extends Controller {
  def invalidAPIPath(path:String) = Action {
    val message = s"Invalid Path [$path] for API"
    BadRequest(Json.obj("status" -> "KO", "message" -> message))
  }
}
