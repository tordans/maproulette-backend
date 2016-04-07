package org.maproulette.controllers.api

import javax.inject.Inject

import org.apache.commons.lang3.StringUtils
import org.maproulette.actions._
import org.maproulette.controllers.CRUDController
import org.maproulette.models.dal.{TagDAL, TaskDAL}
import org.maproulette.models.{Tag, Task}
import org.maproulette.exception.MPExceptionUtil
import org.maproulette.session.{User, SessionManager}
import org.maproulette.utils.Utils
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
                                tagDAL: TagDAL)
  extends CRUDController[Task] {

  // json reads for automatically reading Tasks from a posted json body
  override implicit val tReads: Reads[Task] = Task.taskReads
  // json writes for automatically writing Tasks to a json body response
  override implicit val tWrites: Writes[Task] = Task.taskWrites
  // The type of object that this controller deals with.
  override implicit val itemType = TaskType()
  // json reads for automatically reading Tags from a posted json body
  implicit val tagReads: Reads[Tag] = Tag.tagReads

  /**
    * In this function the task will extract any tags that are supplied with the create json, it will
    * then attempt to create or update the associated tags. The tags can be supplied in 3 different
    * formats:
    * 1. comma separated list of tag names
    * 2. array of full json object structure containing id (optional), name and description of tag
    * 3. comma separated list of tag ids
    *
    * @param body          The Json body of data
    * @param createdObject The Task that was created by the create function
    * @param user the user executing the request
    */
  override def extractAndCreate(body: JsValue, createdObject: Task, user:User): Unit = {
    val tagIds: List[Long] = body \ "tags" match {
      case tags: JsDefined =>
        // this case is for a comma separated list, either of ints or strings
        tags.as[String].split(",").toList.flatMap(tag => {
          try {
            Some(tag.toLong)
          } catch {
            case e: NumberFormatException =>
              // this is the case where a name is supplied, so we will either search for a tag with
              // the same name or create a new tag with the current name
              dal.retrieveByName(tag) match {
                case Some(t) => Some(t.id)
                case None => Some(tagDAL.insert(Tag(-1, tag), user).id)
              }
          }
        })
      case tags: JsUndefined =>
        (body \ "fulltags").asOpt[List[JsValue]] match {
          case Some(tagList) =>
            tagList.map(value => {
              val identifier = (value \ "id").asOpt[Long] match {
                case Some(id) => id
                case None => -1
              }
              Tag.getUpdateOrCreateTag(value, user)(identifier).id
            })
          case None => List.empty
        }
      case _ => List.empty
    }

    // now we have the ids for the supplied tags, then lets map them to the task created
    dal.updateTaskTags(createdObject.id, tagIds, user)
    if (tagIds.nonEmpty) {
      actionManager.setAction(Some(user), itemType.convertToItem(createdObject.id), TagAdded(), tagIds.mkString(","))
    }
  }

  /**
    * Gets a json list of tags of the task
    *
    * @param id The id of the task containing the tags
    * @return The html Result containing json array of tags
    */
  def getTagsForTask(implicit id: Long) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      Ok(Json.toJson(Task(id, "", None, -1, "", None, "").tags))
    }
  }

  /**
    * Gets tasks based on tags, this is regardless of the project or challenge parents.
    *
    * @param tags A comma separated list of tags to match against
    * @param limit The number of tasks to return
    * @param offset The paging offset, incrementing will take you to the next set in the list
    * @return The html Result containing a json array of the found tasks
    */
  def getTasksBasedOnTags(tags: String, limit: Int, offset: Int) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      if (StringUtils.isEmpty(tags)) {
        Utils.badRequest("A comma separated list of tags need to be provided via the query string. Example: ?tags=tag1,tag2")
      } else {
        Ok(Json.toJson(dal.getTasksBasedOnTags(tags.split(",").toList, limit, offset)))
      }
    }
  }

  /**
    * Gets a random task(s) given the provided tags.
    *
    * @param tags A comma separated list of tags to match against
    * @param limit The number of tasks to return
    * @return
    */
  def getRandomTasks(tags: String,
                     limit: Int) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      val result = dal.getRandomTasksStr(None, None, tags.split(",").toList, limit)
      result.foreach(task => actionManager.setAction(user, itemType.convertToItem(task.id), TaskViewed(), ""))
      Ok(Json.toJson(result))
    }
  }

  /**
    * Deletes tags from a given task.
    * Must be authenticated to perform operation
    *
    * @param id The id of the task
    * @param tags A comma separated list of tags to delete
    * @return
    */
  def deleteTagsFromTask(id: Long, tags: String) = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      if (StringUtils.isEmpty(tags)) {
        Utils.badRequest("A comma separated list of tags need to be provided via the query string. Example: ?tags=tag1,tag2")
      } else {
        MPExceptionUtil.internalExceptionCatcher { () =>
          val tagList = tags.split(",").toList
          if (tagList.nonEmpty) {
            dal.deleteTaskStringTags(id, tagList, user)
            actionManager.setAction(Some(user), itemType.convertToItem(id), TagRemoved(), tags)
          }
          NoContent
        }
      }
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
      if (status < Task.STATUS_CREATED || status > Task.STATUS_DELETED) {
        Utils.badRequest(s"Invalid status [$status] provided.")
      } else {
        MPExceptionUtil.internalExceptionCatcher { () =>
          val statusUpdateJson = Json.obj("status" -> status)
          dal.update(statusUpdateJson, user)(id) match {
            case Some(resp) =>
              actionManager.setAction(Some(user), itemType.convertToItem(id), TaskStatusSet(status), "")
              NoContent
            case None => Utils.badRequest(s"Task with id [$id] not found to have status updated.")
          }
        }
      }
    }
  }
}
