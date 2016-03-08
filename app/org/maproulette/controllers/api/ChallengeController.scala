package org.maproulette.controllers.api

import javax.inject.Inject
import org.maproulette.actions.{ActionManager, ChallengeType, TaskViewed}
import org.maproulette.controllers.ParentController
import org.maproulette.models.dal.{ChallengeDAL, TaskDAL}
import org.maproulette.models.{Challenge, Task}
import org.maproulette.session.SessionManager
import play.api.libs.json.{Json, Reads, Writes}
import play.api.mvc.Action

/**
  * @author cuthbertm
  */
class ChallengeController @Inject() (override val childController:TaskController) extends ParentController[Challenge, Task] {
  override protected val dal = ChallengeDAL
  override implicit val tReads: Reads[Challenge] = Challenge.challengeReads
  override implicit val tWrites: Writes[Challenge] = Challenge.challengeWrites
  override protected val cWrites: Writes[Task] = Task.taskWrites
  override protected val cReads: Reads[Task] = Task.taskReads
  override implicit val itemType = ChallengeType()

  def getRandomTasks(projectId: Long,
                     challengeId: Long,
                     tags: String,
                     limit:Int) = Action.async { implicit request =>
    SessionManager.userAwareRequest { implicit user =>
      val result = TaskDAL.getRandomTasksStr(Some(projectId), Some(challengeId), tags.split(",").toList, limit)
      result.foreach(task => ActionManager.setAction(0, itemType.convertToItem(task.id), TaskViewed(), ""))
      Ok(Json.toJson(result))
    }
  }
}
