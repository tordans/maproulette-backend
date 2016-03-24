package controllers

import javax.inject.Inject

import org.maproulette.Config
import org.maproulette.actions._
import org.maproulette.models.dal.{ChallengeDAL, ProjectDAL, SurveyDAL}
import org.maproulette.session.dal.UserDAL
import org.maproulette.session.{SessionManager, User}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import play.api.routing._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Promise
import scala.util.{Failure, Success}

class Application @Inject() (val messagesApi: MessagesApi,
                             sessionManager:SessionManager,
                             userDAL: UserDAL,
                             projectDAL: ProjectDAL,
                             challengeDAL: ChallengeDAL,
                             surveyDAL: SurveyDAL,
                             config:Config) extends Controller with I18nSupport {

  /**
    * The primary entry point to the application
    *
    * @return The index HTML
    */
  def index = Action.async { implicit request =>
    sessionManager.userAwareUIRequest { implicit user =>
      Ok(views.html.index("MapRoulette", User.userOrMocked(user), config)(views.html.main(config.isDebugMode)))
    }
  }

  def adminUIProjectList(limit:Int, offset:Int) =
    adminUIList(Actions.ITEM_TYPE_PROJECT_NAME, None, limit, offset)
  def adminUIChildList(itemType:String, parentId:Long, limit:Int, offset:Int) =
    adminUIList(itemType, Some(parentId), limit, offset)

  /**
    * The generic function used to list elements in the UI
    *
    * @param itemType The type of function you are listing the elements for
    * @param parentId The parent of the objects to list
    * @param limit The amount of elements you want to limit the list result to
    * @param offset For paging
    * @return The html view to show the user
    */
  protected def adminUIList(itemType:String, parentId:Option[Long]=None, limit:Int, offset:Int) = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      val view = Actions.getItemType(itemType) match {
        case Some(it) => it match {
          case ProjectType() =>
            views.html.admin.project(user, projectDAL.listManagedProjects(user, limit, offset))
          case ChallengeType() | SurveyType() =>
            views.html.admin.projectChildren(user, parentId.get,
              projectDAL.listSurveys(limit, offset)(parentId.get),
              projectDAL.listChildren(limit, offset)(parentId.get)
            )
          case _ => views.html.error.error("Invalid item type requested.")
        }
        case None => views.html.error.error("Invalid item type requested.")
      }
      Ok(views.html.index("MapRoulette Administration", user, config)(view))
    }
  }

  def stats = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      Ok(views.html.index("MapRoulette Statistics", user, config)(views.html.admin.stats(user)))
    }
  }

  def users(limit:Int, offset:Int) = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      Ok(views.html.index("MapRoulette Users", user, config)
        (views.html.admin.users(user, userDAL.list(limit, offset)))
      )
    }
  }

  def profile = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
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
    sessionManager.authenticatedFutureUIRequest { implicit user =>
      val p = Promise[Result]
      sessionManager.refreshProfile(user.osmProfile.requestToken, user) onComplete {
        case Success(result) => p success Redirect(routes.Application.index())
        case Failure(f) => p failure f
      }
      p.future
    }
  }

  def error(error:String) = Action.async { implicit request =>
    sessionManager.userAwareUIRequest { implicit user =>
      Ok(views.html.index("Map Roulette Error", User.userOrMocked(user), config)(views.html.error.error(error)))
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
        routes.javascript.AuthController.generateAPIKey,
        routes.javascript.AuthController.deleteUser,
        routes.javascript.Application.error,
        org.maproulette.controllers.api.routes.javascript.ProjectController.delete,
        org.maproulette.controllers.api.routes.javascript.ChallengeController.delete,
        org.maproulette.controllers.api.routes.javascript.SurveyController.delete
      )
    ).as("text/javascript")
  }
}
