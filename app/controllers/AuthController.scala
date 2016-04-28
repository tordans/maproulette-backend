package controllers

import com.google.inject.Inject
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import org.maproulette.Config
import org.maproulette.controllers.ControllerHelper
import org.maproulette.exception.{InvalidException, MPExceptionUtil, NotFoundException}
import org.maproulette.models.dal.DALManager
import org.maproulette.session.{SessionManager, User}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller, Result}

import scala.concurrent.Promise
import scala.util.{Failure, Success}

/**
  * All the authentication actions go in this class
  *
  * @author cuthbertm
  */
class AuthController @Inject() (val messagesApi: MessagesApi,
                                override val webJarAssets: WebJarAssets,
                                sessionManager:SessionManager,
                                override val dalManager:DALManager,
                                val config:Config) extends Controller with I18nSupport with ControllerHelper {

  import scala.concurrent.ExecutionContext.Implicits.global

  /**
    * An action to call to authenticate a user using OAuth 1.0a against the OAuth OSM Provider
    *
    * @return Redirects back to the index page containing a valid session
    */
  def authenticate() = Action.async { implicit request =>
    MPExceptionUtil.internalAsyncUIExceptionCatcher(User.guestUser, config, dalManager) { () =>
      val p = Promise[Result]
      val redirect = request.getQueryString("redirect").getOrElse("")
      request.getQueryString("oauth_verifier") match {
        case Some(verifier) =>
          sessionManager.retrieveUser(verifier) onComplete {
            case Success(user) =>
              // We received the authorized tokens in the OAuth object - store it before we proceed
              p success Redirect(redirect, SEE_OTHER)
                .withSession(SessionManager.KEY_TOKEN -> user.osmProfile.requestToken.token,
                  SessionManager.KEY_SECRET -> user.osmProfile.requestToken.secret,
                  SessionManager.KEY_USER_ID -> user.id.toString,
                  SessionManager.KEY_OSM_ID -> user.osmProfile.id.toString,
                  SessionManager.KEY_USER_TICK -> DateTime.now().getMillis.toString
                )
            case Failure(e) => p failure e
          }
        case None =>
          val referer = request.headers.get(REFERER)
          val redirectURL = if (StringUtils.isEmpty(redirect) && referer.isDefined) {
            referer.get
          } else if (StringUtils.isNotEmpty(redirect)) {
            val applicationIndex = routes.Application.index().absoluteURL()
            s"${applicationIndex.substring(0, applicationIndex.length - 1)}$redirect"
          } else {
            routes.Application.index().absoluteURL()
          }
          sessionManager.retrieveRequestToken(routes.AuthController.authenticate().absoluteURL() + s"?redirect=$redirectURL") match {
            case Right(t) => {
              // We received the unauthorized tokens in the OAuth object - store it before we proceed
              p success Redirect(sessionManager.redirectUrl(t.token))
                .withSession(SessionManager.KEY_TOKEN -> t.token,
                  SessionManager.KEY_SECRET -> t.secret,
                  SessionManager.KEY_USER_TICK -> DateTime.now().getMillis.toString
                )
            }
            case Left(e) => p failure e
          }
      }
      p.future
    }
  }

  /**
    * Signs out the user, creating essentially a blank new session and redirects user to the index page
    *
    * @return The index html page
    */
  def signOut() = Action { implicit request =>
    Redirect(routes.Application.index()).withNewSession
  }

  def deleteUser(userId:Long) = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      if (user.isSuperUser) {
        Ok(Json.obj("message" -> s"${dalManager.user.delete(userId, user)} User deleted by super user ${user.name} [${user.id}]."))
      } else {
        throw new IllegalAccessException(s"User ${user.name} [${user.id} does not have super user access to delete other users")
      }
    }
  }

  /**
    * Generates a new API key for the user. A user can then use the API key to make API calls directly against
    * the server. Only the current API key for the user will work on any authenticated API calls, any previous
    * keys are immediately discarded once a new one is created.
    *
    * @return Will return NoContent if cannot create the key (which most likely means that no user was
    *         found, or will return the api key as plain text.
    */
  def generateAPIKey() = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      user match {
        case Some(u) => u.generateAPIKey.apiKey match {
          case Some(api) => Ok(api)
          case None => NoContent
        }
        case None => NoContent
      }
    }
  }

  /**
    * Adds a user to the Admin group for a project
    *
    * @param projectId The id of the project to add the user too
    * @return NoContent
    */
  def addUserToProject(userId:Long, projectId:Long) = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      dalManager.user.retrieveById(userId) match {
        case Some(addUser) =>
          if (addUser.groups.exists(_.projectId == projectId)) {
            throw new InvalidException(s"User ${addUser.name} is already part of project $projectId")
          }
          dalManager.user.addUserToProject(addUser, projectId)
          Ok(Json.obj("status" -> "Ok", "message" -> s"User ${addUser.name} added to project $projectId"))
        case None => throw new NotFoundException(s"Could not find user with ID $userId")
      }
    }
  }
}
