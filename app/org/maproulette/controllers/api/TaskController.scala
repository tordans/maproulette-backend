package org.maproulette.controllers.api

import javax.inject.Inject

import org.maproulette.actions._
import org.maproulette.controllers.CRUDController
import org.maproulette.models.dal.{TagDAL, TaskDAL}
import org.maproulette.models.{Tag, Task}
import org.maproulette.exception.NotFoundException
import org.maproulette.session.{SearchParameters, SessionManager, User}
import play.api.libs.json._
import play.api.mvc.Action

/**
  * The Task controller handles all operations for the Task objects.
  * This includes CRUD operations and searching/listing.
  * See {@link org.maproulette.controllers.CRUDController} for more details on CRUD object operations
  *
  * @author cuthbertm
  */
class TaskController @Inject() (override val sessionManager: SessionManager,
                                override val actionManager: ActionManager,
                                override val dal:TaskDAL,
                                override val tagDAL: TagDAL)
  extends CRUDController[Task] with TagsMixin[Task] {

  // json reads for automatically reading Tasks from a posted json body
  override implicit val tReads: Reads[Task] = Task.taskReads
  // json writes for automatically writing Tasks to a json body response
  override implicit val tWrites: Writes[Task] = Task.taskWrites
  // The type of object that this controller deals with.
  override implicit val itemType = TaskType()
  // json reads for automatically reading Tags from a posted json body
  implicit val tagReads: Reads[Tag] = Tag.tagReads
  override def dalWithTags = dal


  /**
    * Function can be implemented to extract more information than just the default create data,
    * to build other objects with the current object at the core. No data will be returned from this
    * function, it purely does work in the background AFTER creating the current object
    *
    * @param body          The Json body of data
    * @param createdObject The object that was created by the create function
    * @param user          The user that is executing the function
    */
  override def extractAndCreate(body: JsValue, createdObject: Task, user: User): Unit = extractTags(body, createdObject, user)

  /**
    * Gets a json list of tags of the task
    *
    * @param id The id of the task containing the tags
    * @return The html Result containing json array of tags
    */
  def getTagsForTask(implicit id: Long) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      Ok(Json.toJson(Task(id, "", -1, "", None, "").tags))
    }
  }

  /**
    * Gets a random task(s) given the provided tags.
    *
    * @param projectSearch Filter on the name of the project
    * @param challengeSearch Filter on the name of the challenge (Survey included)
    * @param challengeTags Filter on the tags of the challenge
    * @param tags A comma separated list of tags to match against
    * @param taskSearch Filter based on the name of the task
    * @param limit The number of tasks to return
    * @return
    */
  def getRandomTasks(projectSearch:String,
                     challengeSearch:String,
                     challengeTags:String,
                     tags: String,
                     taskSearch: String,
                     limit: Int) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      val params = SearchParameters(
        projectSearch = projectSearch,
        challengeSearch = challengeSearch,
        challengeTags = challengeTags.split(",").toList,
        taskTags = tags.split(",").toList,
        taskSearch = taskSearch
      )
      val result = dal.getRandomTasks(User.userOrMocked(user), params, limit)
      result.foreach(task => actionManager.setAction(user, itemType.convertToItem(task.id), TaskViewed(), ""))
      Ok(Json.toJson(result))
    }
  }

  /**
    * Sets the task status to Deleted, this is a case where a task is deleted by the project admin,
    * or user within the admin group for that project. The task will not be deleted in the
    * database but flagged and then ignored. It will continue to be used for statistics.
    * Must be authenticated to perform operation
    *
    * @param id The id of the task
    * @return See {@see this#setTaskStatus} for information
    */
  def setTaskStatusDeleted(id: Long) = setTaskStatus(id, Task.STATUS_DELETED)

  /**
    * Sets the task status to false positive, this is the case where the task is incorrect in
    * stating that a problem exists. Ie. the task really isn't a task
    * Must be authenticated to perform operation
    *
    * @param id the id of the task
    * @return See {@see this#setTaskStatus} for information
    */
  def setTaskStatusFalsePositive(id: Long) = setTaskStatus(id, Task.STATUS_FALSE_POSITIVE)

  /**
    * Sets the task to fixed, this is the case where the user fixed the data in OSM.
    * Must be authenticated to perform operation
    *
    * @param id the id of the task
    * @return See {@see this#setTaskStatus} for information
    */
  def setTaskStatusFixed(id: Long) = setTaskStatus(id, Task.STATUS_FIXED)

  /**
    * Sets the task to skipped, this is the case where a user either doesn't know how to fix the
    * issue or doesn't want to fix it.
    * Must be authenticated to perform operation
    *
    * @param id the id of the task
    * @return See {@see this#setTaskStatus} for information
    */
  def setTaskStatusSkipped(id: Long) = setTaskStatus(id, Task.STATUS_SKIPPED)

  /**
    * Sets the task to already fixed, this is the case where when looking at the base OSM data it
    * is found that someone has already fixed the issue
    *
    * @param id The id of the task
    * @return See (@see this#setTaskStatus} for information
    */
  def setTaskStatusAlreadyFixed(id:Long) = setTaskStatus(id, Task.STATUS_ALREADY_FIXED)

  /**
    * This is the generic function that is leveraged by all the specific functions above. So it
    * sets the task status to the specific status ID's provided by those functions.
    * Must be authenticated to perform operation
    *
    * @param id The id of the task
    * @param status The status id to set the task's status to
    * @return 400 BadRequest if status id is invalid or task with supplied id not found.
    *         If successful then 200 NoContent
    */
  private def setTaskStatus(id: Long, status: Int) = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      val task = dal.retrieveById(id) match {
        case Some(t) => t
        case None => throw new NotFoundException(s"Task with $id not found, can not set status.")
      }
      dal.setTaskStatus(task, status, user)
      actionManager.setAction(Some(user), new TaskItem(task.id), TaskStatusSet(status), task.name)
      NoContent
    }
  }
}
