package controllers

import com.google.inject.Inject
import org.maproulette.session.SessionManager
import play.api.mvc.{Result, Action, Controller}
import scala.concurrent.Promise
import scala.util.{Failure, Success}

/**
  * @author cuthbertm
  */
class AuthController @Inject() extends Controller {

  import scala.concurrent.ExecutionContext.Implicits.global

  def authenticate() = Action.async { implicit request =>
    val p = Promise[Result]
    request.getQueryString("oauth_verifier").map { verifier =>
      SessionManager.retrieveUser(verifier) onComplete {
        case Success(user) =>
          // We received the authorized tokens in the OAuth object - store it before we proceed
          p success Redirect(routes.Application.index())
            .withSession("token" -> user.osmProfile.requestToken.token,
              "secret" -> user.osmProfile.requestToken.secret,
              "userId" -> user.id.toString,
              "osmId" -> user.osmProfile.id.toString
            )
        case Failure(e) => p failure e
      }
    }.getOrElse(
      SessionManager.retrieveRequestToken(routes.AuthController.authenticate().absoluteURL()) match {
        case Right(t) => {
          // We received the unauthorized tokens in the OAuth object - store it before we proceed
          p success Redirect(SessionManager.redirectUrl(t.token)).withSession("token" -> t.token, "secret" -> t.secret)
        }
        case Left(e) => p failure e
      })
    p.future
  }

  def signOut() = Action { implicit request =>
    Redirect(routes.Application.index()).withNewSession
  }

  def generateAPIKey() = Action.async { implicit request =>
    SessionManager.userAwareRequest { implicit user =>
      user match {
        case Some(u) => u.generateAPIKey.apiKey match {
          case Some(api) => Ok(api)
          case None => NoContent
        }
        case None => NoContent
      }
    }
  }
}
