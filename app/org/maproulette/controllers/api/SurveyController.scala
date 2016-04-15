package org.maproulette.controllers.api

import javax.inject.Inject

import org.maproulette.actions._
import org.maproulette.controllers.ParentController
import org.maproulette.models.{Survey, Task}
import org.maproulette.models.dal.{SurveyDAL, TaskDAL}
import org.maproulette.session.{SearchParameters, SessionManager}
import play.api.libs.json.{Json, Reads, Writes}
import play.api.mvc.Action

/**
  * The survey controller handles all operations for the Survey objects.
  * This includes CRUD operations and searching/listing.
  * See {@link org.maproulette.controllers.ParentController} for more details on parent object operations
  * See {@link org.maproulette.controllers.CRUDController} for more details on CRUD object operations
  *
  * @author cuthbertm
  */
class SurveyController @Inject() (override val childController:TaskController,
                                  override val sessionManager: SessionManager,
                                  override val actionManager: ActionManager,
                                  override val dal: SurveyDAL,
                                  taskDAL: TaskDAL)
  extends ParentController[Survey, Task] {

  // json reads for automatically reading Challenges from a posted json body
  override implicit val tReads: Reads[Survey] = Survey.surveyReads
  // json writes for automatically writing Challenges to a json body response
  override implicit val tWrites: Writes[Survey] = Survey.surveyWrites
  // json writes for automatically writing Tasks to a json body response
  override protected val cWrites: Writes[Task] = Task.taskWrites
  // json reads for automatically reading tasks from a posted json body
  override protected val cReads: Reads[Task] = Task.taskReads
  // The type of object that this controller deals with.
  override implicit val itemType = SurveyType()

  /**
    * Gets a random task that is a child of the survey.
    *
    * @param surveyId The survey id that is the parent of the tasks that you would be searching for.
    * @param tags A comma separated list of tags that optionally can be used to further filter the tasks
    * @param taskSearch Filter based on the name of the task
    * @param limit Limit of how many tasks should be returned
    * @return A list of Tasks that match the supplied filters
    */
  def getRandomTasks(surveyId: Long,
                     tags: String,
                     taskSearch: String,
                     limit:Int) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      val params = SearchParameters(
        challengeId = Some(surveyId),
        taskTags = tags.split(",").toList,
        taskSearch = taskSearch
      )
      val result = taskDAL.getRandomTasks(params, limit)
      result.foreach(task => actionManager.setAction(user, itemType.convertToItem(task.id), TaskViewed(), ""))
      Ok(Json.toJson(result))
    }
  }

  /**
    * Answers a question for a survey
    *
    * @param surveyId The id of the survey
    * @param taskId The id of the task being viewed
    * @param answerId The id of the answer
    * @return
    */
  def answerSurveyQuestion(surveyId:Long, taskId:Long, answerId:Long) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      val userId = user match {
        case Some(u) => Some(u.id)
        case None => None
      }
      dal.answerQuestion(surveyId, taskId, answerId, user)
      actionManager.setAction(user, itemType.convertToItem(taskId), QuestionAnswered(), "")
      NoContent
    }
  }
}
