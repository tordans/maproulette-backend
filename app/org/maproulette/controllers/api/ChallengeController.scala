package org.maproulette.controllers.api

import javax.inject.Inject

import org.maproulette.actions.{ActionManager, ChallengeType, TaskViewed}
import org.maproulette.controllers.ParentController
import org.maproulette.models.dal.{ChallengeDAL, TaskDAL}
import org.maproulette.models.{Challenge, Task}
import org.maproulette.session.{SearchParameters, SessionManager}
import play.api.libs.json.{Json, Reads, Writes}
import play.api.mvc.Action

/**
  * The challenge controller handles all operations for the Challenge objects.
  * This includes CRUD operations and searching/listing.
  * See {@link org.maproulette.controllers.ParentController} for more details on parent object operations
  * See {@link org.maproulette.controllers.CRUDController} for more details on CRUD object operations
  *
  * @author cuthbertm
  */
class ChallengeController @Inject() (override val childController:TaskController,
                                     override val sessionManager: SessionManager,
                                     override val actionManager: ActionManager,
                                     override val dal: ChallengeDAL,
                                     taskDAL: TaskDAL)
  extends ParentController[Challenge, Task] {

  // json reads for automatically reading Challenges from a posted json body
  override implicit val tReads: Reads[Challenge] = Challenge.challengeReads
  // json writes for automatically writing Challenges to a json body response
  override implicit val tWrites: Writes[Challenge] = Challenge.challengeWrites
  // json writes for automatically writing Tasks to a json body response
  override protected val cWrites: Writes[Task] = Task.taskWrites
  // json reads for automatically reading tasks from a posted json body
  override protected val cReads: Reads[Task] = Task.taskReads
  // The type of object that this controller deals with.
  override implicit val itemType = ChallengeType()

  /**
    * Gets a random task that is a child of the challenge.
    *
    * @param challengeId The challenge id that is the parent of the tasks that you would be searching for.
    * @param taskSearch Filter based on the name of the task
    * @param tags A comma separated list of tags that optionally can be used to further filter the tasks
    * @param limit Limit of how many tasks should be returned
    * @return A list of Tasks that match the supplied filters
    */
  def getRandomTasks(challengeId: Long,
                     taskSearch:String,
                     tags: String,
                     limit:Int) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      val params = SearchParameters(
        challengeId = Some(challengeId),
        taskSearch = taskSearch,
        taskTags = tags.split(",").toList
      )
      val result = taskDAL.getRandomTasks(params, limit)
      result.foreach(task => actionManager.setAction(user, itemType.convertToItem(task.id), TaskViewed(), ""))
      Ok(Json.toJson(result))
    }
  }
}
