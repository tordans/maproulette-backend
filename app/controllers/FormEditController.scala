package controllers

import javax.inject.Inject

import org.apache.commons.lang3.StringUtils
import org.maproulette.Config
import org.maproulette.actions.{Actions, ProjectType}
import org.maproulette.controllers.ControllerHelper
import org.maproulette.exception.NotFoundException
import org.maproulette.models.{Challenge, Project, Survey, Task}
import org.maproulette.models.dal._
import org.maproulette.permissions.Permission
import org.maproulette.services.ChallengeService
import org.maproulette.session.SessionManager
import play.api.db.Database
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{DefaultReads, Json}
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, Controller}

import scala.io.Source

/**
  * @author cuthbertm
  */
class FormEditController @Inject() (val messagesApi: MessagesApi,
                                    ws:WSClient,
                                    override val webJarAssets: WebJarAssets,
                                    sessionManager:SessionManager,
                                    override val dalManager: DALManager,
                                    challengeService: ChallengeService,
                                    val config:Config,
                                    db:Database,
                                    permission: Permission) extends Controller with I18nSupport with ControllerHelper with DefaultReads {

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
          if (c.challengeType == Actions.ITEM_TYPE_SURVEY) {
            val surveyForm = Survey.surveyForm.fill(Survey(clonedChallenge, dalManager.survey.getAnswers(c.id)))
            getOkIndex("MapRoulette Administration", user,
              views.html.admin.forms.surveyForm(user, parentId, surveyForm)
            )
          } else {
            val challengeForm = Challenge.challengeForm.fill(clonedChallenge)
            getOkIndex("MapRoulette Administration", user,
              views.html.admin.forms.challengeForm(user, parentId, challengeForm)
            )
          }
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

  def challengeFormPost(parentId:Long, itemId:Long) = Action.async(parse.multipartFormData) { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      permission.hasWriteAccess(ProjectType(), user)(parentId)
      Challenge.challengeForm.bindFromRequest.fold(
        formWithErrors => {
          getIndex(BadRequest, "MapRoulette Administration", user,
            views.html.admin.forms.challengeForm(user, parentId, formWithErrors)
          )
        },
        challenge => {
          val updatedChallenge = challenge.overpassQL match {
            case Some(ql) if StringUtils.isNotEmpty(ql) => challenge.copy(status = Some(Challenge.STATUS_BUILDING))
            case None => challenge.remoteGeoJson match {
              case Some(url) if StringUtils.isNotEmpty(url) => challenge.copy(status = Some(Challenge.STATUS_BUILDING))
              case None => challenge
            }
          }
          if (itemId > -1) {
            dalManager.challenge.update(Json.toJson(updatedChallenge)(Challenge.challengeWrites), user)(itemId).get
          } else {
            val newChallenge = dalManager.challenge.insert(updatedChallenge, user)

            val uploadData = request.body.file("localGeoJSON") match {
              case Some(f) if StringUtils.isNotEmpty(f.filename) => Some(Source.fromFile(f.ref.file).getLines().mkString)
              case _ => None
            }
            challengeService.buildChallengeTasks(user, newChallenge, uploadData)
          }
          Redirect(routes.Application.adminUIChildList(Actions.ITEM_TYPE_CHALLENGE_NAME, parentId)).flashing("success" -> "Project saved!")
        }
      )
    }
  }

  def rebuildChallenge(parentId:Long, challengeId:Long) = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      permission.hasWriteAccess(ProjectType(), user)(parentId)
      dalManager.challenge.retrieveById(challengeId) match {
        case Some(c) =>
          challengeService.rebuildChallengeTasks(user, c)
          Ok
        case None => throw new NotFoundException(s"No challenge found with id $challengeId")
      }
    }
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
        views.html.admin.forms.surveyForm(user, parentId, surveyForm)
      )
    }
  }

  def surveyFormPost(parentId:Long, itemId:Long) = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      permission.hasWriteAccess(ProjectType(), user)(parentId)
      Survey.surveyForm.bindFromRequest.fold(
        formWithErrors => {
          getIndex(BadRequest, "MapRoulette Administration", user,
            views.html.admin.forms.surveyForm(user, parentId, formWithErrors)
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
