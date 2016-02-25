package controllers

import org.maproulette.actions.{Challenge => challengeType, TaskViewed, ActionManager}
import org.maproulette.controllers.ParentController
import org.maproulette.data.{Task, Challenge}
import org.maproulette.data.dal.{TaskDAL, ChallengeDAL}
import org.maproulette.exception.MPExceptionUtil
import play.api.libs.json.{Json, Writes, Reads}
import play.api.mvc.Action

/**
  * @author cuthbertm
  */
object ChallengeController extends ParentController[Challenge, Task] {
  override protected val dal = ChallengeDAL
  override implicit val tReads: Reads[Challenge] = Challenge.challengeReads
  override implicit val tWrites: Writes[Challenge] = Challenge.challengeWrites
  override protected val cWrites: Writes[Task] = Task.taskWrites
  override protected val cReads: Reads[Task] = Task.taskReads
  override protected val childController = TaskController
  override implicit val itemType = challengeType()

  def getRandomTasks(projectId: Long,
                     challengeId: Long,
                     tags: String,
                     limit:Int) = Action {
    MPExceptionUtil.internalServerCatcher { () =>
      val result = TaskDAL.getRandomTasksStr(Some(projectId), Some(challengeId), tags.split(",").toList, limit)
      result.foreach(task => ActionManager.setAction(0, itemType.convertToItem(task.id), TaskViewed(), ""))
      Ok(Json.toJson(result))
    }
  }
}
