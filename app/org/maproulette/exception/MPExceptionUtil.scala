package org.maproulette.exception

import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.mvc.Results._

/**
  * @author cuthbertm
  */
object MPExceptionUtil {
  def internalExceptionCatcher(block:() => Result) : Result = {
    try {
      block()
    } catch {
      case e:InvalidException =>
        Logger.error(e.getMessage, e)
        BadRequest(Json.obj("status" -> "KO", "message" -> e.getMessage))
      case e:Exception =>
        Logger.error(e.getMessage, e)
        InternalServerError(Json.obj("status" -> "KO", "message" -> e.getMessage))
    }
  }
}
