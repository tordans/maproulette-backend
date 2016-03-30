package controllers

import javax.inject.Inject

import org.maproulette.Config
import org.maproulette.actions.Actions
import org.maproulette.controllers.ControllerHelper
import org.maproulette.models.{Challenge, Project, Survey}
import org.maproulette.models.dal.{ChallengeDAL, ProjectDAL, SurveyDAL}
import org.maproulette.session.SessionManager
import org.maproulette.session.dal.UserDAL
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}

/**
  * @author cuthbertm
  */
class FormEditController @Inject() (val messagesApi: MessagesApi,
                                    sessionManager:SessionManager,
                                    userDAL: UserDAL,
                                    projectDAL: ProjectDAL,
                                    challengeDAL: ChallengeDAL,
                                    surveyDAL: SurveyDAL,
                                    val config:Config) extends Controller with I18nSupport with ControllerHelper {

  def projectFormUI(parentId:Long, itemId:Long, limit:Int, offset:Int) = Action.async { implicit request =>
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
      getOkIndex("MapRoulette Administration", user, views.html.admin.forms.projectForm(parentId, projectForm, Map.empty))
    }
  }

  def projectFormPost(parentId:Long, itemId:Long, limit:Int, offset:Int) = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      Project.projectForm.bindFromRequest.fold(
        formWithErrors => {
          getIndex(BadRequest, "MapRoulette Administration", user,
            views.html.admin.forms.projectForm(parentId, formWithErrors, Map.empty))
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
          Redirect(routes.Application.adminUIProjectList(limit, offset)).flashing("success" -> "Project saved!")
        }
      )
    }
  }

  def challengeFormUI(parentId:Long, itemId:Long, limit:Int, offset:Int) = Action.async { implicit request =>
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
        views.html.admin.forms.challengeForm(parentId, challengeForm,
          Map("Challenges" -> routes.Application.adminUIChildList(Actions.ITEM_TYPE_CHALLENGE_NAME, parentId, limit, offset)))

      )
    }
  }

  def challengeFormPost(parentId:Long, itemId:Long, limit:Int, offset:Int) = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      Challenge.challengeForm.bindFromRequest.fold(
        formWithErrors => {
          getIndex(BadRequest, "MapRoulette Administration", user,
            views.html.admin.forms.challengeForm(parentId, formWithErrors,
              Map("Challenges" -> routes.Application.adminUIChildList(Actions.ITEM_TYPE_CHALLENGE_NAME, parentId, limit, offset)))
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
          Redirect(routes.Application.adminUIChildList(Actions.ITEM_TYPE_CHALLENGE_NAME, parentId, limit, offset)).flashing("success" -> "Project saved!")
        }
      )
    }
  }

  def surveyFormUI(parentId:Long, itemId:Long, limit:Int, offset:Int) = Action.async { implicit request =>
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
        views.html.admin.forms.surveyForm(parentId, surveyForm,
          Map("Surveys" -> routes.Application.adminUIChildList(Actions.ITEM_TYPE_SURVEY_NAME, parentId, limit, offset)))
      )
    }
  }

  def surveyFormPost(parentId:Long, itemId:Long, limit:Int, offset:Int) = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      Survey.surveyForm.bindFromRequest.fold(
        formWithErrors => {
          getIndex(BadRequest, "MapRoulette Administration", user,
            views.html.admin.forms.surveyForm(parentId, formWithErrors,
              Map("Surveys" -> routes.Application.adminUIChildList(Actions.ITEM_TYPE_SURVEY_NAME, parentId, limit, offset)))
          )
        },
        survey => {
          val id = if (itemId > -1) {
            implicit val answerWrites = Survey.answerWrites
            surveyDAL.update(Json.toJson(survey)(Survey.surveyWrites), user)(itemId)
            itemId
          } else {
            val newSurvey = surveyDAL.insert(survey, user)
            newSurvey.id
          }
          Redirect(routes.Application.adminUIChildList(Actions.ITEM_TYPE_SURVEY_NAME, parentId, limit, offset)).flashing("success" -> "Project saved!")
        }
      )
    }
  }

}
