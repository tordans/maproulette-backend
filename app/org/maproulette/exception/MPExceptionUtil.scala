package org.maproulette.exception

import oauth.signpost.exception.OAuthNotAuthorizedException
import org.maproulette.Config
import org.maproulette.session.User
import play.api.Logger
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.mvc.{Request, Result}
import play.api.mvc.Results._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
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
      case e:IllegalAccessException =>
        Logger.error(e.getMessage, e)
        Forbidden(Json.obj("status" -> "Forbidden", "Message" -> e.getMessage))
      case e:NotFoundException =>
        Logger.error(e.getMessage, e)
        NotFound(Json.obj("status" -> "NotFound", "Message" -> e.getMessage))
      case e:Exception =>
        Logger.error(e.getMessage, e)
        InternalServerError(Json.obj("status" -> "KO", "message" -> e.getMessage))
    }
  }

  /**
    * Same as the internalExceptionCatcher, except specific to UI requests so instead of returning
    * a JSON payload, it will redirect to an error page
    *
    * @param block The block of code to be executed
    * @return The error page with the error that occurred.
    */
  def internalUIExceptionCatcher(config:Config)(block:() => Result)(implicit request:Request[Any], messages:Messages) : Result = {
    val tempUser = User.guestUser.copy(theme = "skin-red")
    try {
      block()
    } catch {
      case e:InvalidException =>
        Logger.error(e.getMessage, e)
        BadRequest(views.html.index("Map Roulette Error", tempUser, config)(views.html.error.error(e.getMessage)))
      case e:IllegalAccessException =>
        Logger.error(e.getMessage, e)
        Forbidden(views.html.index("Map Roulette Error", tempUser, config)(views.html.error.error("Forbidden: " + e.getMessage)))
      case e:NotFoundException =>
        Logger.error(e.getMessage, e)
        NotFound(views.html.index("Map Roulette Error", tempUser, config)(views.html.error.error("Not Found: " + e.getMessage)))
      case e:Exception =>
        Logger.error(e.getMessage, e)
        InternalServerError(views.html.index("Map Roulette Error", tempUser, config)(views.html.error.error("Internal Server Error: " + e.getMessage)))
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
        case e:IllegalAccessException =>
          Logger.error(e.getMessage, e)
          p success Forbidden(Json.obj("status" -> "Forbidden", "message" -> e.getMessage))
        case e:NotFoundException =>
          Logger.error(e.getMessage, e)
          p success NotFound(Json.obj("status" -> "NotFound", "Message" -> e.getMessage))
        case e:Exception =>
          Logger.error(e.getMessage, e)
          p success InternalServerError(Json.obj("status" -> "KO", "message" -> e.getMessage))
      }
    }
    p.future
  }

  def internalAsyncUIExceptionCatcher(config:Config)(block:() => Future[Result])(implicit request:Request[Any], messages:Messages) : Future[Result] = {
    val p = Promise[Result]
    val tempUser = User.guestUser.copy(theme = "skin-red")
    block() onComplete {
      case Success(result) => p success result
      case Failure(f) => f match {
        case e:InvalidException =>
          Logger.error(e.getMessage, e)
          p success BadRequest(views.html.index("Map Roulette Error", tempUser, config)(views.html.error.error(e.getMessage)))
        case e:OAuthNotAuthorizedException =>
          Logger.error(e.getMessage, e)
          p success Unauthorized(views.html.index("Map Roulette Error", tempUser, config)(views.html.error.error("Unauthorized: " + e.getMessage)))
        case e:IllegalAccessException =>
          Logger.error(e.getMessage, e)
          p success Forbidden(views.html.index("Map Roulette Error", tempUser, config)(views.html.error.error("Forbidden: " + e.getMessage)))
        case e:NotFoundException =>
          Logger.error(e.getMessage, e)
          p success NotFound(views.html.index("Map Roulette Error", tempUser, config)(views.html.error.error("Not Found: " + e.getMessage)))
        case e:Exception =>
          Logger.error(e.getMessage, e)
          p success InternalServerError(views.html.index("Map Roulette Error", tempUser, config)(views.html.error.error("Internal Server Error: " + e.getMessage)))
      }
    }
    p.future
  }
}
