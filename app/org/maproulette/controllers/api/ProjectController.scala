package org.maproulette.controllers.api

import javax.inject.Inject

import io.swagger.annotations.Api
import org.apache.commons.lang3.StringUtils
import org.maproulette.actions.{ActionManager, ProjectType, TaskViewed}
import org.maproulette.controllers.ParentController
import org.maproulette.models.dal.{ProjectDAL, TaskDAL}
import org.maproulette.models.{Challenge, ClusteredPoint, Project}
import org.maproulette.session.{SearchParameters, SessionManager, User}
import org.maproulette.utils.Utils
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent}

/**
  * The project controller handles all operations for the Project objects.
  * This includes CRUD operations and searching/listing.
  * See {@link org.maproulette.controllers.ParentController} for more details on parent object operations
  * See {@link org.maproulette.controllers.CRUDController} for more details on CRUD object operations
  *
  * @author cuthbertm
  */
@Api(value = "/Project", description = "Operations for Projects", protocols = "http")
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

  implicit val writes = ClusteredPoint.clusteredPointWrites

  /**
    * This function allows sub classes to modify the body, primarily this would be used for inserting
    * default elements into the body that shouldn't have to be required to create an object.
    *
    * @param body The incoming body from the request
    * @return
    */
  override def updateCreateBody(body: JsValue, user:User): JsValue = {
    var jsonBody = super.updateCreateBody(body, user)
    jsonBody = Utils.insertIntoJson(jsonBody, "groups", Array.emptyShortArray)(arrayWrites[Short])
    Utils.insertIntoJson(jsonBody, "enabled", true)(BooleanWrites)
  }

  /**
    * We override the base function and force -1 as the parent, as projects do not have parents.
    */
  override def readByName(id: Long, name: String): Action[AnyContent] = super.readByName(-1, name)

  /**
    * Gets a random task that is an descendant of the project.
    *
    * @param projectId The project id, ie. the ancestor of the child.
    * @param challengeSearch Filter based on the name of the challenge
    * @param challengeTags Filter based on the tags of the challenge
    * @param tags A comma separated list of tags that optionally can be used to further filter the tasks
    * @param taskSearch Filter based on the name of the task
    * @param limit Limit of how many tasks should be returned
    * @return A list of Tasks that match the supplied filters
    */
  def getRandomTasks(projectId: Long,
                     challengeSearch:String,
                     challengeTags:String,
                     tags: String,
                     taskSearch:String,
                     limit:Int) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      val params = SearchParameters(
        projectId = Some(projectId),
        challengeSearch = challengeSearch,
        challengeTags = challengeTags.split(",").toList,
        taskSearch = taskSearch,
        taskTags = tags.split(",").toList
      )
      val result = taskDAL.getRandomTasks(User.userOrMocked(user), params, limit)
      result.foreach(task => actionManager.setAction(user, itemType.convertToItem(task.id), TaskViewed(), ""))
      Ok(Json.toJson(result))
    }
  }

  def getSearchedClusteredPoints(searchCookie:String) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      val searchParams = SearchParameters.convert(searchCookie)
      Ok(Json.toJson(dal.getSearchedClusteredPoints(searchParams)))
    }
  }

  def getClusteredPoints(projectId:Long, challengeIds:String) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      val pid = if (projectId < 0) {
        None
      } else {
        Some(projectId)
      }
      val cids = if (StringUtils.isEmpty(challengeIds)) {
        List.empty
      } else {
        challengeIds.split(",").map(_.toLong).toList
      }
      Ok(Json.toJson(dal.getClusteredPoints(pid, cids)))
    }
  }
}
