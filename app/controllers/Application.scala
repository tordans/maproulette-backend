package controllers

import javax.inject.Inject

import org.maproulette.Config
import org.maproulette.models.dal.ProjectDAL
import org.maproulette.session.dal.UserDAL
import org.maproulette.session.{User, SessionManager}
import play.api.mvc._
import play.api.routing._

class Application @Inject() (sessionManager:SessionManager,
                             userDAL: UserDAL,
                             projectDAL: ProjectDAL,
                             config:Config) extends Controller {

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

  def projects(limit:Int, offset:Int) = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      Ok(views.html.index("MapRoulette Project Administration", user, config)
        (views.html.admin.project(user, projectDAL.listManagedProjects(user, limit, offset)))
      )
    }
  }

  def challenges = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      Ok(views.html.index("MapRoulette Challenge Administration", user, config)(views.html.admin.challenge(user)))
    }
  }

  def tasks = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      Ok(views.html.index("MapRoulette Task Administration", user, config)(views.html.admin.task(user)))
    }
  }

  def stats = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      Ok(views.html.index("MapRoulette Statistics", user, config)(views.html.admin.stats(user)))
    }
  }

  def users(limit:Int, offset:Int) = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      Ok(views.html.index("MapRoulette Users", user, config)
        (views.html.admin.users(user, userDAL.list(limit, offset)))
      )
    }
  }

  def profile = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      Ok(views.html.index("MapRoulette Profile", user, config)
        (views.html.user.profile(user))
      )
    }
  }

  /**
    * Action to refresh the user's OSM profile, this will reload the index page
    *
    * @return The index html
    */
  def refreshProfile = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      sessionManager.refreshProfile(user.osmProfile.requestToken, user)
      Redirect(routes.Application.index())
    }
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
