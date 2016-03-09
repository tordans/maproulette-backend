package org.maproulette.exception

import oauth.signpost.exception.OAuthNotAuthorizedException
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.mvc.Results._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Promise, Future}
import scala.util.{Failure, Success}

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

  def internalAsyncExceptionCatcher(block:() => Future[Result]) : Future[Result] = {
    val p = Promise[Result]
    block() onComplete {
      case Success(result) => p success result
      case Failure(f) => f match {
        case e:InvalidException =>
          Logger.error(e.getMessage, e)
          p success BadRequest(Json.obj("status" -> "KO", "message" -> e.getMessage))
        case e:OAuthNotAuthorizedException =>
          Logger.error(e.getMessage, e)
          p success Unauthorized(Json.obj("status" -> "NotAuthorized", "message" -> e.getMessage))
        case e:Exception =>
          Logger.error(e.getMessage, e)
          p success InternalServerError(Json.obj("status" -> "KO", "message" -> e.getMessage))
      }
    }
    p.future
  }
}
