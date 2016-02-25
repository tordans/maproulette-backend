package controllers

import org.maproulette.actions.{Project => projectType, TaskViewed, ActionManager}
import org.maproulette.controllers.ParentController
import org.maproulette.data.{Challenge, Project}
import org.maproulette.data.dal.{TaskDAL, ProjectDAL}
import org.maproulette.exception.MPExceptionUtil
import play.api.libs.json._
import play.api.mvc.Action

/**
  * @author cuthbertm
  */
object ProjectController extends ParentController[Project, Challenge] {
  override protected val dal = ProjectDAL
  override implicit val tReads: Reads[Project] = Project.projectReads
  override implicit val tWrites: Writes[Project] = Project.projectWrites
  override protected val cWrites: Writes[Challenge] = Challenge.challengeWrites
  override protected val cReads: Reads[Challenge] = Challenge.challengeReads
  override protected val childController = ChallengeController
  override implicit val itemType = projectType()

  def getRandomTasks(projectId: Long,
                     tags: String,
                     limit:Int) = Action {
    MPExceptionUtil.internalServerCatcher { () =>
      val result = TaskDAL.getRandomTasksStr(Some(projectId), None, tags.split(",").toList, limit)
      result.foreach(task => ActionManager.setAction(0, itemType.convertToItem(task.id), TaskViewed(), ""))
      Ok(Json.toJson(result))
    }
  }
}
