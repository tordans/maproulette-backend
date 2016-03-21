package controllers

import javax.inject.Inject

import org.maproulette.Config
import org.maproulette.actions.Actions
import org.maproulette.models.{Challenge, Project, Survey}
import org.maproulette.models.dal.{ChallengeDAL, ProjectDAL, SurveyDAL}
import org.maproulette.session.SessionManager
import org.maproulette.session.dal.UserDAL
import play.api.mvc.{Action, Controller}

/**
  * @author cuthbertm
  */
class FormEditController @Inject() (sessionManager:SessionManager,
                                    userDAL: UserDAL,
                                    projectDAL: ProjectDAL,
                                    challengeDAL: ChallengeDAL,
                                    surveyDAL: SurveyDAL,
                                    config:Config) extends Controller {

  def projectFormUI(parentId:Long, itemId:Long) = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      val project:Project = if (itemId > -1) {
        projectDAL.retrieveById(itemId) match {
          case Some(proj) => proj
          case None => Project.emptyProject
        }
      } else {
        Project.emptyProject
      }
      val projectForm = Project.projectForm.fill(project)
      Ok(views.html.index("MapRoulette Administration", user, config)
        (views.html.admin.forms.projectForm(parentId, projectForm, Map.empty))
      )
    }
  }

  def projectFormPost(parentId:Long, itemId:Long) = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      Ok
    }
  }

  def challengeFormUI(parentId:Long, itemId:Long, limit:Int, offset:Int) = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      val challenge:Challenge = if (itemId > -1) {
        challengeDAL.retrieveById(itemId) match {
          case Some(chal) => chal
          case None => Challenge.emptyChallenge(parentId)
        }
      } else {
        Challenge.emptyChallenge(parentId)
      }
      val challengeForm = Challenge.challengeForm.fill(challenge)
      Ok(views.html.index("MapRoulette Administration", user, config)
        (views.html.admin.forms.challengeForm(parentId, challengeForm,
          Map("Challenges" -> routes.Application.adminUIChildList(Actions.ITEM_TYPE_CHALLENGE_NAME, parentId, limit, offset)))
        )
      )
    }
  }

  def challengeFormPost(parentId:Long, itemId:Long) = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      Ok
    }
  }

  def surveyFormUI(parentId:Long, itemId:Long, limit:Int, offset:Int) = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      val survey:Survey = if (itemId > -1) {
        surveyDAL.retrieveById(itemId) match {
          case Some(sur) => sur
          case None => Survey.emptySurvey(parentId)
        }
      } else {
        Survey.emptySurvey(parentId)
      }
      val surveyForm = Survey.surveyForm.fill(survey)
      Ok(views.html.index("MapRoulette Administration", user, config)
        (views.html.admin.forms.surveyForm(parentId, surveyForm,
          Map("Surveys" -> routes.Application.adminUIChildList(Actions.ITEM_TYPE_SURVEY_NAME, parentId, limit, offset)))
        )
      )
    }
  }

  def surveyFormPost(parentId:Long, itemId:Long) = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      Ok
    }
  }

}
