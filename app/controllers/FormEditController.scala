package controllers

import javax.inject.Inject

import org.maproulette.Config
import org.maproulette.actions.Actions
import org.maproulette.controllers.ControllerHelper
import org.maproulette.models.{Challenge, Project, Survey, Task}
import org.maproulette.models.dal._
import org.maproulette.session.{SessionManager, User}
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{DefaultReads, JsValue, Json}
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, Controller}

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * @author cuthbertm
  */
class FormEditController @Inject() (val messagesApi: MessagesApi,
                                    ws:WSClient,
                                    override val webJarAssets: WebJarAssets,
                                    sessionManager:SessionManager,
                                    override val dalManager: DALManager,
                                    val config:Config) extends Controller with I18nSupport with ControllerHelper with DefaultReads {
  import scala.concurrent.ExecutionContext.Implicits.global

  def projectFormUI(parentId:Long, itemId:Long) = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      val project:Project = if (itemId > -1) {
        dalManager.project.retrieveById(itemId) match {
          case Some(proj) => proj
          case None => Project.emptyProject
        }
      } else {
        Project.emptyProject
      }
      val projectForm = Project.projectForm.fill(project)
      getOkIndex("MapRoulette Administration", user, views.html.admin.forms.projectForm(user, parentId, projectForm, Map.empty))
    }
  }

  def projectFormPost(parentId:Long, itemId:Long) = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      Project.projectForm.bindFromRequest.fold(
        formWithErrors => {
          getIndex(BadRequest, "MapRoulette Administration", user,
            views.html.admin.forms.projectForm(user, parentId, formWithErrors, Map.empty))
        },
        project => {
          val id = if (itemId > -1) {
            implicit val groupWrites = Project.groupWrites
            dalManager.project.update(Json.toJson(project)(Project.projectWrites), user)(itemId)
            itemId
          } else {
            val newProject = dalManager.project.insert(project, user)
            newProject.id
          }
          Redirect(routes.Application.adminUIProjectList()).flashing("success" -> "Project saved!")
        }
      )
    }
  }

  def challengeFormUI(parentId:Long, itemId:Long) = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      val challenge:Challenge = if (itemId > -1) {
        dalManager.challenge.retrieveById(itemId) match {
          case Some(chal) => chal
          case None => Challenge.emptyChallenge(parentId)
        }
      } else {
        Challenge.emptyChallenge(parentId)
      }
      val challengeForm = Challenge.challengeForm.fill(challenge)
      getOkIndex("MapRoulette Administration", user,
        views.html.admin.forms.challengeForm(user, parentId, challengeForm)
      )
    }
  }

  def challengeFormPost(parentId:Long, itemId:Long) = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      Challenge.challengeForm.bindFromRequest.fold(
        formWithErrors => {
          getIndex(BadRequest, "MapRoulette Administration", user,
            views.html.admin.forms.challengeForm(user, parentId, formWithErrors)
          )
        },
        challenge => {
          val id = if (itemId > -1) {
            dalManager.challenge.update(Json.toJson(challenge)(Challenge.challengeWrites), user)(itemId)
            Future { buildOverpassQLTasks(challenge, user) }
            itemId
          } else {
            val newChallenge = dalManager.challenge.insert(challenge, user)
            Future { buildOverpassQLTasks(newChallenge, user) }
            newChallenge.id
          }
          Redirect(routes.Application.adminUIChildList(Actions.ITEM_TYPE_CHALLENGE_NAME, parentId)).flashing("success" -> "Project saved!")
        }
      )
    }
  }

  /**
    * Based on the supplied overpass query this will generate the tasks for the challenge
    *
    * @param challenge The challenge to create the tasks under
    * @param user The user executing the query
    */
  def buildOverpassQLTasks(challenge:Challenge, user:User) = {
    challenge.overpassQL match {
      case Some(ql) =>
        // run the query and then create the tasks
        val osmQLProvider = config.getOSMQLProvider
        val query = s"[out:json];${ql.split('\n').map(_.trim.filter(_ >= ' ')).mkString}out body geom;"
        val jsonFuture = ws.url(osmQLProvider.providerURL).withRequestTimeout(osmQLProvider.requestTimeout).post(query).map { _.json }

        jsonFuture onComplete {
          case Success(s) =>
            // parse the results
            val elements = (s \ "elements").as[List[JsValue]]

            elements.foreach {
              element =>
                try {
                  val geometry = (element \ "type").asOpt[String] match {
                    case Some("way") =>
                      val points = (element \ "geometry").as[List[JsValue]].map {
                        geom => List((geom \ "lon").as[Double], (geom \ "lat").as[Double])
                      }
                      Some(Json.obj("type" -> "LineString", "coordinates" -> points))
                    case Some("node") =>
                      Some(Json.obj(
                        "type" -> "Point",
                        "coordinates" -> List((element \ "lon").as[Double], (element \ "lat").as[Double])
                      ))
                    case _ => None
                  }

                  geometry match {
                    case Some(geom) =>
                      val props = (element \ "tags").asOpt[JsValue] match {
                        case Some(tags) => tags
                        case None => Json.obj()
                      }
                      val newTask = Task(-1,
                        (element \ "id").as[Int]+"",
                        challenge.id,
                        challenge.instruction,
                        None,
                        Json.obj(
                          "type" -> "FeatureCollection",
                          "features" -> Json.arr(Json.obj(
                            "id" -> (element \ "id").as[Int],
                            "geometry" -> geom,
                            "properties" -> props
                          ))
                        ).toString
                      )

                      try {
                        dalManager.task.insert(newTask, user)
                      } catch {
                        // this task could fail on unique key violation, we need to ignore them
                        case e:Exception =>
                          Logger.error(e.getMessage)
                      }
                    case None => None
                  }
                } catch {
                  case e:Exception =>
                    Logger.error(e.getMessage, e)
                }
            }
          case Failure(f) => throw f
        }
      case None => // just ignore, we don't have to do anything if it wasn't set
    }
  }

  def surveyFormUI(parentId:Long, itemId:Long) = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      val survey:Survey = if (itemId > -1) {
        dalManager.survey.retrieveById(itemId) match {
          case Some(sur) => sur
          case None => Survey.emptySurvey(parentId)
        }
      } else {
        Survey.emptySurvey(parentId)
      }
      val surveyForm = Survey.surveyForm.fill(survey)
      getOkIndex("MapRoulette Administration", user,
        views.html.admin.forms.surveyForm(parentId, surveyForm)
      )
    }
  }

  def surveyFormPost(parentId:Long, itemId:Long) = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      Survey.surveyForm.bindFromRequest.fold(
        formWithErrors => {
          getIndex(BadRequest, "MapRoulette Administration", user,
            views.html.admin.forms.surveyForm(parentId, formWithErrors)
          )
        },
        survey => {
          val id = if (itemId > -1) {
            implicit val answerWrites = Survey.answerWrites
            dalManager.survey.update(Json.toJson(survey)(Survey.surveyWrites), user)(itemId)
            itemId
          } else {
            val newSurvey = dalManager.survey.insert(survey, user)
            newSurvey.challenge.id
          }
          Redirect(routes.Application.adminUIChildList(Actions.ITEM_TYPE_SURVEY_NAME, parentId)).flashing("success" -> "Project saved!")
        }
      )
    }
  }

  def taskFormUI(projectId:Long, parentId:Long, parentType:String, itemId:Long) = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      val task:Task = if (itemId > -1) {
        dalManager.task.retrieveById(itemId) match {
          case Some(t) => t
          case None => Task.emptyTask(parentId)
        }
      } else {
        Task.emptyTask(parentId)
      }
      val taskForm = Task.taskForm.fill(task)
      getOkIndex("MapRoulette Administration", user,
        views.html.admin.forms.taskForm(projectId, parentId, parentType, taskForm)
      )
    }
  }

  def taskFormPost(projectId:Long, parentId:Long, parentType:String, itemId:Long) = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      Task.taskForm.bindFromRequest.fold(
        formWithErrors => {
          getIndex(BadRequest, "MapRoulette Administration", user,
            views.html.admin.forms.taskForm(projectId, parentId, parentType, formWithErrors)
          )
        },
        task => {
          val id = if (itemId > -1) {
            dalManager.task.update(Json.toJson(task), user)(itemId)
            itemId
          } else {
            val newTask = dalManager.task.insert(task, user)
            newTask.id
          }
          Redirect(routes.Application.adminUITaskList(projectId, parentType, parentId)).flashing("success" -> "Project saved!")
        }
      )
    }
  }
}
