package org.maproulette.controllers.api

import org.maproulette.actions.{ActionManager, ChallengeType, TaskViewed}
import org.maproulette.controllers.ParentController
import org.maproulette.data.dal.{ChallengeDAL, TaskDAL}
import org.maproulette.data.{Challenge, Task}
import org.maproulette.exception.MPExceptionUtil
import play.api.libs.json.{Json, Reads, Writes}
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
  override implicit val itemType = ChallengeType()

  def getRandomTasks(projectId: Long,
                     challengeId: Long,
                     tags: String,
                     limit:Int) = Action {
    MPExceptionUtil.internalExceptionCatcher { () =>
      val result = TaskDAL.getRandomTasksStr(Some(projectId), Some(challengeId), tags.split(",").toList, limit)
      result.foreach(task => ActionManager.setAction(0, itemType.convertToItem(task.id), TaskViewed(), ""))
      Ok(Json.toJson(result))
    }
  }
}
