package controllers

import javax.inject.Inject

import org.maproulette.session.{User, SessionManager}
import play.api.mvc._
import play.api.routing._

class Application @Inject() extends Controller {

  def index = Action.async { implicit request =>
    SessionManager.userAwareRequest { implicit user =>
      Ok(views.html.index("MapRoulette", User.userOrMocked(user))(views.html.main()))
    }
  }

  def testing = Action {
    Ok(views.html.testing())
  }

  def javascriptRoutes = Action { implicit request =>
    Ok(
      JavaScriptReverseRouter("jsRoutes")(
        routes.javascript.AuthController.generateAPIKey
      )
    ).as("text/javascript")
  }
}
