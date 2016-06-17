package org.maproulette.controllers.api

import java.sql.Connection
import javax.inject.Inject

import io.swagger.annotations.Api
import org.maproulette.actions._
import org.maproulette.controllers.ParentController
import org.maproulette.exception.NotFoundException
import org.maproulette.models.{Answer, Survey, Task}
import org.maproulette.models.dal.{SurveyDAL, TagDAL, TaskDAL}
import org.maproulette.session.{SearchParameters, SessionManager, User}
import org.maproulette.utils.Utils
import play.api.libs.json._
import play.api.mvc.Action

/**
  * The survey controller handles all operations for the Survey objects.
  * This includes CRUD operations and searching/listing.
  * See {@link org.maproulette.controllers.ParentController} for more details on parent object operations
  * See {@link org.maproulette.controllers.CRUDController} for more details on CRUD object operations
  *
  * @author cuthbertm
  */
@Api(value = "/Survey", description = "Operations for Surveys", protocols = "http")
class SurveyController @Inject() (override val childController:TaskController,
                                  override val sessionManager: SessionManager,
                                  override val actionManager: ActionManager,
                                  override val dal: SurveyDAL,
                                  taskDAL: TaskDAL,
                                  override val tagDAL: TagDAL)
  extends ParentController[Survey, Task] with TagsMixin[Survey] {

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

  override def dalWithTags = dal

  private implicit val answerWrites = Survey.answerWrites

  /**
    * This function allows sub classes to modify the body, primarily this would be used for inserting
    * default elements into the body that shouldn't have to be required to create an object.
    *
    * @param body The incoming body from the request
    * @return
    */
  override def updateCreateBody(body: JsValue, user:User): JsValue = {
    val jsonBody = super.updateCreateBody(body, user:User)
    var challengeBody = (jsonBody \ "challenge").as[JsValue]
    challengeBody = Utils.insertJsonID(challengeBody)
    challengeBody = Utils.insertIntoJson(challengeBody, "enabled", true)(BooleanWrites)
    challengeBody = Utils.insertIntoJson(challengeBody, "challengeType", Actions.ITEM_TYPE_SURVEY)(IntWrites)
    challengeBody = Utils.insertIntoJson(challengeBody, "featured", false)(BooleanWrites)

    val returnBody = Utils.insertIntoJson(jsonBody, "challenge", challengeBody, true)(JsValueWrites)
    //if answers are supplied in a simple json string array, then convert to the answer types
    val answerArray = (challengeBody \ "answers").as[List[String]].map(a => Answer(answer = a))
    Utils.insertIntoJson(returnBody, "answers", answerArray, true)
  }

  /**
    * Function can be implemented to extract more information than just the default create data,
    * to build other objects with the current object at the core. No data will be returned from this
    * function, it purely does work in the background AFTER creating the current object
    *
    * @param body          The Json body of data
    * @param createdObject The object that was created by the create function
    * @param user          The user that is executing the function
    */
  override def extractAndCreate(body: JsValue, createdObject: Survey, user: User)(implicit c:Connection=null): Unit = {
    super.extractAndCreate(body, createdObject, user)
    extractTags(body, createdObject, user)
  }

  /**
    * Gets a json list of tags of the Survey
    *
    * @param id The id of the survey containing the tags
    * @return The html Result containing json array of tags
    */
  def getTagsForSurvey(implicit id: Long) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      Ok(Json.toJson(getTags(id)))
    }
  }

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
      val result = taskDAL.getRandomTasks(User.userOrMocked(user), params, limit)
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
    sessionManager.authenticatedRequest { implicit user =>
      // make sure that the survey and answer exists first
      dal.retrieveById(surveyId) match {
        case Some(survey) =>
          survey.answers.find(_.id == answerId) match {
            case Some(a) =>
              dal.answerQuestion(survey, taskId, answerId, user)
              actionManager.setAction(Some(user), itemType.convertToItem(taskId), QuestionAnswered(answerId), a.answer)
              NoContent
            case None =>
              throw new NotFoundException(s"Requested answer [$answerId] for survey does not exist.")
          }
        case None => throw new NotFoundException(s"Requested survey [$surveyId] to answer question from does not exist.")
      }
    }
  }
}
