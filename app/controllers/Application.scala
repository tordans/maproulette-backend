package controllers

import javax.inject.Inject

import org.maproulette.Config
import org.maproulette.session.{User, SessionManager}
import play.api.mvc._
import play.api.routing._

class Application @Inject() (sessionManager:SessionManager, config:Config) extends Controller {

  /**
    * The primary entry point to the application
    *
    * @return The index HTML
    */
  def index = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      Ok(views.html.index("MapRoulette", User.userOrMocked(user), config)(views.html.main(config.isDebugMode)))
    }
  }

  def projects = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      Ok(views.html.index("MapRoulette Admin", user, config)(views.html.admin.project(user)))
    }
  }

  def challenges = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      Ok(views.html.index("MapRoulette Admin", user, config)(views.html.admin.challenge(user)))
    }
  }

  def tasks = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      Ok(views.html.index("MapRoulette Admin", user, config)(views.html.admin.task(user)))
    }
  }

  def admin = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      Ok(views.html.index("MapRoulette Admin", user, config)(views.html.admin.main(user)))
    }
  }

  /**
    * Action to refresh the user's OSM profile, this will reload the index page
    *
    * @return The index html
    */
  def refreshProfile = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      sessionManager.refreshProfile(user.osmProfile.requestToken)
      Redirect(routes.Application.index())
    }
  }

  /**
    * REMOVE: ONLY FOR TESTING PURPOSES
    *
    * @return The testing html page
    */
  def testing = Action {
    Ok(views.html.testing(config.isDebugMode))
  }

  /**
    * Maps specific actions to javascripts reverse routes, so that we can call the actions in javascript
    *
    * @return The results of whatever action is called by the javascript
    */
  def javascriptRoutes = Action { implicit request =>
    Ok(
      JavaScriptReverseRouter("jsRoutes")(
        routes.javascript.AuthController.generateAPIKey
      )
    ).as("text/javascript")
  }
}
