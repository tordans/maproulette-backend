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
  * Function wrappers that wrap our code blocks in try catches.
  *
  * @author cuthbertm
  */
object MPExceptionUtil {
  /**
    * Used for Actions, wraps the code block and if InvalidException found will send a BadRequest,
    * all other exceptions sent back as InternalServerError
    *
    * @param block The block of code to be executed expecting a Result back
    * @return Result
    */
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

  /**
    * Used for async Actions, so the expected result from the block of code is a Future,
    * on success will simply pass the result on, on failure will throw either a BadRequest,
    * Unauthorized or InternalServerError
    *
    * @param block The block of code to be executed expecting a Future[Result] back
    * @return Future[Result]
    */
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
