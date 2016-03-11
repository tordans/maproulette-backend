package org.maproulette.controllers.api

import javax.inject.Inject
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}

/**
  * A basic action controller for miscellaneous operations on the API
  *
  * @author cuthbertm
  */
class APIController @Inject() extends Controller {

  /**
    * In the routes file this will be mapped to any /api/v2/ paths. It is the last mapping to take
    * place so if it doesn't match any of the other routes it will fall into this invalid path.
    *
    * @param path The path found after /api/v2
    * @return A json object returned with a 400 BadRequest
    */
  def invalidAPIPath(path:String) = Action {
    val message = s"Invalid Path [$path] for API"
    BadRequest(Json.obj("status" -> "KO", "message" -> message))
  }
}
