package org.maproulette.controllers.api

import javax.inject.Inject
import org.maproulette.actions.{ActionManager, ProjectType, TaskViewed}
import org.maproulette.controllers.ParentController
import org.maproulette.models.dal.{ProjectDAL, TaskDAL}
import org.maproulette.models.{Challenge, Project}
import org.maproulette.session.SessionManager
import play.api.libs.json._
import play.api.mvc.Action

/**
  * @author cuthbertm
  */
class ProjectController @Inject() (override val childController:ChallengeController) extends ParentController[Project, Challenge] {
  override protected val dal = ProjectDAL
  override implicit val tReads: Reads[Project] = Project.projectReads
  override implicit val tWrites: Writes[Project] = Project.projectWrites
  override protected val cWrites: Writes[Challenge] = Challenge.challengeWrites
  override protected val cReads: Reads[Challenge] = Challenge.challengeReads
  override implicit val itemType = ProjectType()

  def getRandomTasks(projectId: Long,
                     tags: String,
                     limit:Int) = Action.async { implicit request =>
    SessionManager.userAwareRequest { implicit user =>
      val result = TaskDAL.getRandomTasksStr(Some(projectId), None, tags.split(",").toList, limit)
      result.foreach(task => ActionManager.setAction(0, itemType.convertToItem(task.id), TaskViewed(), ""))
      Ok(Json.toJson(result))
    }
  }
}
