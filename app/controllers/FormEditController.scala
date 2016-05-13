package controllers

import javax.inject.Inject

import org.apache.commons.lang3.StringUtils
import org.maproulette.Config
import org.maproulette.actions.{Actions, ProjectType}
import org.maproulette.controllers.ControllerHelper
import org.maproulette.exception.{InvalidException, NotFoundException}
import org.maproulette.models.{Challenge, Project, Survey, Task}
import org.maproulette.models.dal._
import org.maproulette.permissions.Permission
import org.maproulette.session.{SessionManager, User}
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{DefaultReads, JsValue, Json}
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, Controller}

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

/**
  * @author cuthbertm
  */
class FormEditController @Inject() (val messagesApi: MessagesApi,
                                    ws:WSClient,
                                    override val webJarAssets: WebJarAssets,
                                    sessionManager:SessionManager,
                                    override val dalManager: DALManager,
                                    val config:Config,
                                    permission: Permission) extends Controller with I18nSupport with ControllerHelper with DefaultReads {
  import scala.concurrent.ExecutionContext.Implicits.global

  def projectFormUI(parentId:Long, itemId:Long) = Action.async { implicit request =>
    implicit val requireSuperUser = true
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
    implicit val requireSuperUser = true
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

  def cloneChallengeFormUI(parentId:Long, itemId:Long) = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      permission.hasWriteAccess(ProjectType(), user)(parentId)
      dalManager.challenge.retrieveById(itemId) match {
        case Some(c) =>
          val clonedChallenge = c.copy(id = -1)
          val challengeForm = Challenge.challengeForm.fill(clonedChallenge)
          getOkIndex("MapRoulette Administration", user,
            views.html.admin.forms.challengeForm(user, parentId, challengeForm)
          )
        case None =>
          throw new NotFoundException(s"No challenge found to clone matching the given id [$itemId]")
      }
    }
  }

  def challengeFormUI(parentId:Long, itemId:Long) = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      permission.hasWriteAccess(ProjectType(), user)(parentId)
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
      permission.hasWriteAccess(ProjectType(), user)(parentId)
      Challenge.challengeForm.bindFromRequest.fold(
        formWithErrors => {
          getIndex(BadRequest, "MapRoulette Administration", user,
            views.html.admin.forms.challengeForm(user, parentId, formWithErrors)
          )
        },
        challenge => {
          val overpassChallenge = challenge.overpassQL match {
            case Some(ql) if StringUtils.isNotEmpty(ql) => challenge.copy(overpassStatus = Some(Challenge.OVERPASS_STATUS_BUILDING))
            case None => challenge
          }
          val newChallenge = if (itemId > -1) {
            dalManager.challenge.update(Json.toJson(overpassChallenge)(Challenge.challengeWrites), user)(itemId).get
          } else {
            dalManager.challenge.insert(overpassChallenge, user)
          }
          Future { buildOverpassQLTasks(newChallenge, user) }
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
  private def buildOverpassQLTasks(challenge:Challenge, user:User) = {
    challenge.overpassQL match {
      case Some(ql) if StringUtils.isNotEmpty(ql) =>
        // run the query and then create the tasks
        val osmQLProvider = config.getOSMQLProvider
        val timeoutPattern = "\\[timeout:([\\d]*)\\]".r
        val timeout = timeoutPattern.findAllIn(ql).matchData.toList.headOption match {
          case Some(m) => Duration(m.group(1).toInt, "seconds")
          case None => osmQLProvider.requestTimeout
        }

        val jsonFuture = ws.url(osmQLProvider.providerURL).withRequestTimeout(timeout).post(parseQuery(ql))

        jsonFuture onComplete {
          case Success(result) =>
            if (result.status == OK) {
              var partial = false;
              val payload = result.json
              // parse the results
              val elements = (payload \ "elements").as[List[JsValue]]
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
                      partial = true
                      Logger.error(e.getMessage, e)
                  }
              }
              partial match {
                case true =>
                  dalManager.challenge.update(Json.obj("overpassStatus" -> Challenge.OVERPASS_STATUS_PARTIALLY_LOADED), user)(challenge.id)
                case false =>
                  dalManager.challenge.update(Json.obj("overpassStatus" -> Challenge.OVERPASS_STATUS_COMPLETE), user)(challenge.id)
              }
            } else {
              dalManager.challenge.update(Json.obj("overpassStatus" -> Challenge.OVERPASS_STATUS_FAILED), user)(challenge.id)
              throw new InvalidException(s"Bad Request: ${result.body}")
            }
          case Failure(f) =>
            dalManager.challenge.update(Json.obj("overpassStatus" -> Challenge.OVERPASS_STATUS_FAILED), user)(challenge.id)
            throw f
        }
      case None => // just ignore, we don't have to do anything if it wasn't set
    }
  }

  /**
    * parse the query, replace various extended overpass query parameters see http://wiki.openstreetmap.org/wiki/Overpass_turbo/Extended_Overpass_Queries
      Currently do not support {{bbox}} or {{center}}
    *
    * @param query The query to parse
    * @return
    */
  private def parseQuery(query:String) : String = {
    val osmQLProvider = config.getOSMQLProvider
    // User can set their own custom timeout if the want
    var replacedQuery = if (query.indexOf("[timeout:") == 0) {
      s"[out:json]$query"
    } else {
      s"[out:json][timeout:${osmQLProvider.requestTimeout.toSeconds}];$query"
    }
    // execute regex matching against {{data:string}}, {{geocodeId:name}}, {{geocodeArea:name}}, {{geocodeBbox:name}}, {{geocodeCoords:name}}
    replacedQuery
  }

  def surveyFormUI(parentId:Long, itemId:Long) = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      permission.hasWriteAccess(ProjectType(), user)(parentId)
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
      permission.hasWriteAccess(ProjectType(), user)(parentId)
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
      permission.hasWriteAccess(ProjectType(), user)(projectId)
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
      permission.hasWriteAccess(ProjectType(), user)(projectId)
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
