package controllers

import javax.inject.Inject

import org.maproulette.Config
import org.maproulette.actions._
import org.maproulette.controllers.ControllerHelper
import org.maproulette.models.dal.{ChallengeDAL, ProjectDAL, SurveyDAL}
import org.maproulette.session.dal.UserDAL
import org.maproulette.session.{SessionManager, User}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsNumber, Json}
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
                             val config:Config) extends Controller with I18nSupport with ControllerHelper {

  /**
    * The primary entry point to the application
    *
    * @return The index HTML
    */
  def index = Action.async { implicit request =>
    sessionManager.userAwareUIRequest { implicit user =>
      val userOrMocked = User.userOrMocked(user)
      getOkIndex("MapRoulette", userOrMocked, views.html.main(userOrMocked, config.isDebugMode))
    }
  }

  /**
    * Only slightly different to the index page, this one shows the geojson of a specific item on the
    * map, which then can be edited or status set
    *
    * @param parentId The parent of the task (either challenge or survey)
    * @param taskId The task itself
    * @return The html view to show the user
    */
  def map(parentId:Long, taskId:Long) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      val userOrMocked = User.userOrMocked(user)
      getOkIndex("MapRoulette", userOrMocked, views.html.main(userOrMocked, config.isDebugMode, parentId, taskId))
    }
  }

  def adminUIProjectList() = adminUIList(Actions.ITEM_TYPE_PROJECT_NAME, "", None)
  def adminUIChildList(itemType:String, parentId:Long) = adminUIList(itemType, "", Some(parentId))

  /**
    * The generic function used to list elements in the UI
    *
    * @param itemType The type of function you are listing the elements for
    * @param parentId The parent of the objects to list
    * @return The html view to show the user
    */
  protected def adminUIList(itemType:String, parentType:String, parentId:Option[Long]=None) = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      // For now we are ignoring the limit and offset properties and letting the UI handle it completely
      val limitIgnore = 10000
      val offsetIgnore = 0
      val view = Actions.getItemType(itemType) match {
        case Some(it) => it match {
          case ProjectType() =>
            views.html.admin.project(user, projectDAL.listManagedProjects(user, limitIgnore, offsetIgnore, false))
          case ChallengeType() | SurveyType() =>
            views.html.admin.projectChildren(user, parentId.get,
              projectDAL.listSurveys(limitIgnore, offsetIgnore, false)(parentId.get),
              projectDAL.listChildren(limitIgnore, offsetIgnore, false)(parentId.get),
              if (it == ChallengeType()) { 0 } else { 1 }
            )
          case _ => views.html.error.error("Invalid item type requested.")
        }
        case None => views.html.error.error("Invalid item type requested.")
      }
      getOkIndex("MapRoulette Administration", user, view)
    }
  }

  def adminUITaskList(projectId:Long, parentType:String, parentId:Long) = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      getOkIndex("MapRoulette Administration", user, views.html.admin.task(user, projectId, parentType, parentId))
    }
  }

  def stats = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      getOkIndex("MapRoulette Statistics", user, views.html.admin.stats(user))
    }
  }

  def users(limit:Int, offset:Int, q:String) = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      getOkIndex("MapRoulette Users", user,
        views.html.admin.users.users(user,
          userDAL.list(limit, offset, false, q),
          projectDAL.listManagedProjects(user)
        )
      )
    }
  }

  def profile = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      getOkIndex("MapRoulette Profile", user, views.html.user.profile(user))
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

  /**
    * Routes to the error page
    *
    * @param error
    * @return
    */
  def error(error:String) = Action.async { implicit request =>
    sessionManager.userAwareUIRequest { implicit user =>
      getOkIndex("MapRoulette Error", User.userOrMocked(user), views.html.error.error(error))
    }
  }

  /**
    * Special API for handling data table API requests for tasks
    *
    * @param parentType Either "Challenge" or "Survey"
    * @param parentId The id of the parent
    * @return
    */
  def taskDataTableList(parentType:String, parentId:Long) = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      val parentDAL = Actions.getItemType(parentType) match {
        case Some(pt) => pt match {
          case ChallengeType() => Some(challengeDAL)
          case SurveyType() => Some(surveyDAL)
        }
        case None => None
      }
      val postData = request.body.asInstanceOf[AnyContentAsFormUrlEncoded].data
      val draw = postData.get("draw").head.head.toInt
      val start = postData.get("start").head.head.toInt
      val length = postData.get("length").head.head.toInt
      val search = postData.get("search[value]").head.head
      val orderDirection = postData.get("order[0][dir]").head.head.toUpperCase
      val orderColumnID = postData.get("order[0][column]").head.head.toInt
      val orderColumnName = postData.get(s"columns[$orderColumnID][name]").head.head
      val response = parentDAL match {
        case Some(dal) =>
          val tasks = dal.listChildren(length, start, false, search, orderColumnName, orderDirection)(parentId)
          val taskMap = tasks.map(task => Map(
            "id" -> task.id.toString,
            "name" -> task.name,
            "identifier" -> task.identifier.getOrElse(""),
            "instruction" -> task.instruction,
            "location" -> task.location.toString,
            "status" -> task.status.getOrElse(0).toString,
            "actions" -> task.id.toString
          ))

          Json.obj(
            "draw" -> JsNumber(draw),
            "recordsTotal" -> JsNumber(dal.getTotalChildren()(parentId)),
            "recordsFiltered" -> JsNumber(dal.getTotalChildren(searchString = search)(parentId)),
            "data" -> Json.toJson(taskMap)
          )
        case None =>
          Json.obj(
            "draw" -> JsNumber(draw),
            "error" -> "Invalid parent type."
          )
      }
      Ok(response)
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
        org.maproulette.controllers.api.routes.javascript.SurveyController.delete,
        org.maproulette.controllers.api.routes.javascript.TaskController.delete,
        routes.javascript.MappingController.getTaskDisplayGeoJSON
      )
    ).as("text/javascript")
  }
}
