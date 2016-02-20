package controllers

import org.maproulette.controllers.ParentController
import org.maproulette.data.{Task, Challenge}
import org.maproulette.data.dal.{TaskDAL, ChallengeDAL}
import play.api.Logger
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

  def getRandomTasks(projectId: Long,
                     challengeId: Long,
                     tags: String,
                     limit:Int) = Action {
    try {
      Ok(Json.toJson(TaskDAL.getRandomTasksStr(Some(projectId), Some(challengeId), tags.split(",").toList, limit)))
    } catch {
      case e: Exception =>
        Logger.error(e.getMessage, e)
        InternalServerError(Json.obj("status" -> "KO", "message" -> e.getMessage))
    }
  }
}
