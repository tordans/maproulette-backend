package org.maproulette.exception

import controllers.WebJarAssets
import oauth.signpost.exception.OAuthNotAuthorizedException
import org.maproulette.Config
import org.maproulette.actions.ActionManager
import org.maproulette.models.dal.{ChallengeDAL, DALManager}
import org.maproulette.session.User
import play.api.Logger
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.mvc.{Request, Result}
import play.api.mvc.Results._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}

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
        NotFound(Json.obj("status" -> "NotFound", "message" -> e.getMessage))
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
  def internalUIExceptionCatcher(user:User, config:Config, dalManager:DALManager)(block:() => Result)
                                (implicit request:Request[Any], messages:Messages, webJarAssets: WebJarAssets) : Result = {
    val tempUser = user.copy(theme = "skin-red")
    val featuredChallenges = dalManager.challenge.getFeaturedChallenges(config.numberOfChallenges, 0)
    val hotChallenges = dalManager.challenge.getHotChallenges(config.numberOfChallenges, 0)
    val newChallenges = dalManager.challenge.getNewChallenges(config.numberOfChallenges, 0)
    val activity = dalManager.action.getRecentActivity(user.id, config.numberOfActivities, 0)
    try {
      block()
    } catch {
      case e:InvalidException =>
        Logger.error(e.getMessage, e)
        BadRequest(views.html.index("Map Roulette Error", tempUser, config,
          hotChallenges, newChallenges, featuredChallenges, activity)
          (views.html.error.error(e.getMessage)))
      case e:IllegalAccessException =>
        Logger.error(e.getMessage, e)
        Forbidden(views.html.index("Map Roulette Error", tempUser, config,
          hotChallenges, newChallenges, featuredChallenges, activity)
          (views.html.error.error("Forbidden: " + e.getMessage)))
      case e:NotFoundException =>
        Logger.error(e.getMessage, e)
        NotFound(views.html.index("Map Roulette Error", tempUser, config,
          hotChallenges, newChallenges, featuredChallenges, activity)
          (views.html.error.error("Not Found: " + e.getMessage)))
      case e:Exception =>
        Logger.error(e.getMessage, e)
        InternalServerError(views.html.index("Map Roulette Error", tempUser, config,
          hotChallenges, newChallenges, featuredChallenges, activity)
          (views.html.error.error("Internal Server Error: " + e.getMessage)))
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
    Try(block()) match {
      case Success(f) => f onComplete {
        case Success(result) => p success result
        case Failure(e) => p success manageException(e)
      }
      case Failure(e) => p success manageException(e)
    }
    p.future
  }

  private def manageException(e:Throwable) : Result = {
    e match {
      case e:InvalidException =>
        Logger.error(e.getMessage, e)
        BadRequest(Json.obj("status" -> "KO", "message" -> e.getMessage))
      case e:OAuthNotAuthorizedException =>
        Logger.error(e.getMessage, e)
        Unauthorized(Json.obj("status" -> "NotAuthorized", "message" -> e.getMessage)).withNewSession
      case e:IllegalAccessException =>
        Logger.error(e.getMessage, e)
        Forbidden(Json.obj("status" -> "Forbidden", "message" -> e.getMessage))
      case e:NotFoundException =>
        Logger.error(e.getMessage, e)
        NotFound(Json.obj("status" -> "NotFound", "message" -> e.getMessage))
      case e:Throwable =>
        Logger.error(e.getMessage, e)
        InternalServerError(Json.obj("status" -> "KO", "message" -> e.getMessage))
    }
  }

  def internalAsyncUIExceptionCatcher(user:User, config:Config, dalManager:DALManager)(block:() => Future[Result])
                                     (implicit request:Request[Any], messages:Messages,
                                      webJarAssets: WebJarAssets) : Future[Result] = {
    val p = Promise[Result]
    val tempUser = user.copy(theme = "skin-red")
    Try(block()) match {
      case Success(s) => s onComplete {
        case Success(result) => p success result
        case Failure(f) => p success manageUIException(f, tempUser, config, dalManager)
      }
      case Failure(f) => p success manageUIException(f, tempUser, config, dalManager)
    }
    p.future
  }

  private def manageUIException(e:Throwable, user:User, config:Config, dalManager:DALManager)
                               (implicit request:Request[Any], messages:Messages, webJarAssets: WebJarAssets) : Result = {
    val featuredChallenges = dalManager.challenge.getFeaturedChallenges(config.numberOfChallenges, 0)
    val hotChallenges = dalManager.challenge.getHotChallenges(config.numberOfChallenges, 0)
    val newChallenges = dalManager.challenge.getNewChallenges(config.numberOfChallenges, 0)
    val activities = dalManager.action.getRecentActivity(user.id, config.numberOfChallenges, 0)
    e match {
      case e:InvalidException =>
        Logger.error(e.getMessage, e)
        BadRequest(views.html.index("Map Roulette Error", user, config,
          hotChallenges, newChallenges, featuredChallenges, activities)
          (views.html.error.error(e.getMessage)))
      case e:OAuthNotAuthorizedException =>
        Logger.error(e.getMessage, e)
        Unauthorized(views.html.index("Map Roulette Error", user, config,
          hotChallenges, newChallenges, featuredChallenges, activities)
          (views.html.error.error("Unauthorized: " + e.getMessage, "Unauthorized", 401))).withNewSession
      case e:IllegalAccessException =>
        Logger.error(e.getMessage, e)
        Forbidden(views.html.index("Map Roulette Error", user, config,
          hotChallenges, newChallenges, featuredChallenges, activities)
          (views.html.error.error("Forbidden: " + e.getMessage, "Forbidden", 403)))
      case e:NotFoundException =>
        Logger.error(e.getMessage, e)
        NotFound(views.html.index("Map Roulette Error", user, config,
          hotChallenges, newChallenges, featuredChallenges, activities)
          (views.html.error.error("Not Found: " + e.getMessage)))
      case e:Throwable =>
        Logger.error(e.getMessage, e)
        InternalServerError(views.html.index("Map Roulette Error", user, config,
          hotChallenges, newChallenges, featuredChallenges, activities)
          (views.html.error.error("Internal Server Error: " + e.getMessage)))
    }
  }
}
