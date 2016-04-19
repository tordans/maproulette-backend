package controllers

import javax.inject.Inject

import org.maproulette.Config
import org.maproulette.actions.Actions
import org.maproulette.controllers.ControllerHelper
import org.maproulette.models.{Challenge, Project, Survey, Task}
import org.maproulette.models.dal.{ChallengeDAL, ProjectDAL, SurveyDAL, TaskDAL}
import org.maproulette.session.SessionManager
import org.maproulette.session.dal.UserDAL
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}

/**
  * @author cuthbertm
  */
class FormEditController @Inject() (val messagesApi: MessagesApi,
                                    override val webJarAssets: WebJarAssets,
                                    sessionManager:SessionManager,
                                    userDAL: UserDAL,
                                    projectDAL: ProjectDAL,
                                    override val challengeDAL: ChallengeDAL,
                                    surveyDAL: SurveyDAL,
                                    taskDAL: TaskDAL,
                                    val config:Config) extends Controller with I18nSupport with ControllerHelper {

  def projectFormUI(parentId:Long, itemId:Long) = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      val project:Project = if (itemId > -1) {
        projectDAL.retrieveById(itemId) match {
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
            projectDAL.update(Json.toJson(project)(Project.projectWrites), user)(itemId)
            itemId
          } else {
            val newProject = projectDAL.insert(project, user)
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
        challengeDAL.retrieveById(itemId) match {
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
            challengeDAL.update(Json.toJson(challenge)(Challenge.challengeWrites), user)(itemId)
            itemId
          } else {
            val newChallenge = challengeDAL.insert(challenge, user)
            newChallenge.id
          }
          Redirect(routes.Application.adminUIChildList(Actions.ITEM_TYPE_CHALLENGE_NAME, parentId)).flashing("success" -> "Project saved!")
        }
      )
    }
  }

  def surveyFormUI(parentId:Long, itemId:Long) = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      val survey:Survey = if (itemId > -1) {
        surveyDAL.retrieveById(itemId) match {
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
            surveyDAL.update(Json.toJson(survey)(Survey.surveyWrites), user)(itemId)
            itemId
          } else {
            val newSurvey = surveyDAL.insert(survey, user)
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
        taskDAL.retrieveById(itemId) match {
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
            taskDAL.update(Json.toJson(task), user)(itemId)
            itemId
          } else {
            val newTask = taskDAL.insert(task, user)
            newTask.id
          }
          Redirect(routes.Application.adminUITaskList(projectId, parentType, parentId)).flashing("success" -> "Project saved!")
        }
      )
    }
  }
}
