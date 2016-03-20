package controllers

import javax.inject.Inject

import org.maproulette.Config
import org.maproulette.actions._
import org.maproulette.models.dal.ProjectDAL
import org.maproulette.session.dal.UserDAL
import org.maproulette.session.{SessionManager, User}
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

  /**
    * The generic function used to list elements in the UI
    *
    * @param itemType The type of function you are listing the elements for
    * @param limit The amount of elements you want to limit the list result to
    * @param offset For paging
    * @return The html view to show the user
    */
  def adminList(itemType:String, limit:Int, offset:Int) = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      val view = Actions.getItemType(itemType) match {
        case Some(it) => it match {
          case ProjectType() =>
            views.html.admin.project(user, projectDAL.listManagedProjects(user, limit, offset))
          case ChallengeType() =>
            views.html.admin.challenge(user)
          case SurveyType() =>
            views.html.admin.survey(user)
          //case TaskType() =>
          case _ => views.html.error.error("Invalid item type requested.")
        }
        case None => views.html.error.error("Invalid item type requested.")
      }
      Ok(views.html.index("MapRoulette Administration", user, config)(view))
    }
  }

  /**
    * Generic function used for displaying the create forms for the various item types
    *
    * @param itemType The item type that you want to create
    * @return The UI form
    */
  def adminCreate(itemType:String) = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      val view = Actions.getItemType(itemType) match {
        case Some(it) => it match {
          case ProjectType() =>
            views.html.admin.forms.projectCreate()
          case ChallengeType() =>
            views.html.admin.forms.challengeCreate()
          case SurveyType() =>
            views.html.admin.forms.surveyCreate()
          case _ => views.html.error.error("Invalid item type requested.")
        }
        case None => views.html.error.error("Invalid item type requested.")
      }
      Ok(views.html.index("MapRoulette Administration", user, config)(view))
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
