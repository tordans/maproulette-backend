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
  * The project controller handles all operations for the Project objects.
  * This includes CRUD operations and searching/listing.
  * See {@link org.maproulette.controllers.ParentController} for more details on parent object operations
  * See {@link org.maproulette.controllers.CRUDController} for more details on CRUD object operations
  *
  * @author cuthbertm
  */
class ProjectController @Inject() (override val childController:ChallengeController,
                                   override val sessionManager:SessionManager,
                                   override val actionManager: ActionManager,
                                   override val dal:ProjectDAL,
                                   taskDAL: TaskDAL)
  extends ParentController[Project, Challenge] {

  // json reads for automatically reading Projects from a posted json body
  override implicit val tReads: Reads[Project] = Project.projectReads
  // json writes for automatically writing Projects to a json body response
  override implicit val tWrites: Writes[Project] = Project.projectWrites
  // json writes for automatically writing Challenges to a json body response
  override protected val cWrites: Writes[Challenge] = Challenge.challengeWrites
  // json reads for automatically reading Challenges from a posted json body
  override protected val cReads: Reads[Challenge] = Challenge.challengeReads
  // The type of object that this controller deals with.
  override implicit val itemType = ProjectType()

  /**
    * Gets a random task that is an descendant of the project.
    *
    * @param projectId The project id, ie. the ancestor of the child.
    * @param tags A comma separated list of tags that optionally can be used to further filter the tasks
    * @param limit Limit of how many tasks should be returned
    * @return A list of Tasks that match the supplied filters
    */
  def getRandomTasks(projectId: Long,
                     tags: String,
                     limit:Int) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      val result = taskDAL.getRandomTasksStr(Some(projectId), None, tags.split(",").toList, limit)
      result.foreach(task => actionManager.setAction(0, itemType.convertToItem(task.id), TaskViewed(), ""))
      Ok(Json.toJson(result))
    }
  }
}
