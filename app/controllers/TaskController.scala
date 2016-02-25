package controllers

import org.apache.commons.lang3.StringUtils
import org.maproulette.actions.{Task => taskType, _}
import org.maproulette.controllers.CRUDController
import org.maproulette.data.dal.{TagDAL, TaskDAL}
import org.maproulette.data.{Tag, Task}
import org.maproulette.exception.MPExceptionUtil
import org.maproulette.utils.Utils
import play.api.libs.json._
import play.api.mvc.Action

/**
  * @author cuthbertm
  */
object TaskController extends CRUDController[Task] {
  override protected val dal = TaskDAL
  override implicit val tReads: Reads[Task] = Task.taskReads
  override implicit val tWrites: Writes[Task] = Task.taskWrites
  override implicit val itemType = taskType()
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
    */
  override def extractAndCreate(body: JsValue, createdObject: Task): Unit = {
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
              TagDAL.retrieveByName(tag) match {
                case Some(t) => Some(t.id)
                case None => Some(TagDAL.insert(Tag(-1, tag)).id)
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
              Tag.getUpdateOrCreateTag(value)(identifier).id
            })
          case None => List.empty
        }
      case _ => List.empty
    }

    // now we have the ids for the supplied tags, then lets map them to the task created
    TaskDAL.updateTaskTags(createdObject.id, tagIds)
    if (tagIds.nonEmpty) {
      ActionManager.setAction(0, itemType.convertToItem(createdObject.id), TagAdded(), tagIds.mkString(","))
    }
  }

  def getTagsForTask(implicit id: Long) = Action {
    Ok(Json.toJson(Task(id, "", None, -1, "", Json.parse("{}")).tags))
  }

  def getTasksBasedOnTags(tags: String, limit: Int, offset: Int) = Action { implicit request =>
    MPExceptionUtil.internalServerCatcher { () =>
      if (StringUtils.isEmpty(tags)) {
        Utils.badRequest("A comma separated list of tags need to be provided via the query string. Example: ?tags=tag1,tag2")
      } else {
        Ok(Json.toJson(dal.getTasksBasedOnTags(tags.split(",").toList, limit, offset)))
      }
    }
  }

  def getRandomTasks(tags: String,
                     limit: Int) = Action {
    MPExceptionUtil.internalServerCatcher { () =>
      val result = dal.getRandomTasksStr(None, None, tags.split(",").toList, limit)
      result.foreach(task => ActionManager.setAction(0, itemType.convertToItem(task.id), TaskViewed(), ""))
      Ok(Json.toJson(result))
    }
  }

  def deleteTagsFromTask(id: Long, tags: String) = Action {
    if (StringUtils.isEmpty(tags)) {
      Utils.badRequest("A comma separated list of tags need to be provided via the query string. Example: ?tags=tag1,tag2")
    } else {
      MPExceptionUtil.internalServerCatcher { () =>
        val tagList = tags.split(",").toList
        if (tagList.nonEmpty) {
          TaskDAL.deleteTaskStringTags(id, tagList)
          ActionManager.setAction(0, itemType.convertToItem(id), TagRemoved(), tags)
        }
        NoContent
      }
    }
  }

  def setTaskStatusDeleted(id: Long) = setTaskStatus(id, Task.STATUS_DELETED)
  def setTaskStatusFalsePositive(id: Long) = setTaskStatus(id, Task.STATUS_FALSE_POSITIVE)
  def setTaskStatusFixed(id: Long) = setTaskStatus(id, Task.STATUS_FIXED)
  def setTaskStatisSkipped(id: Long) = setTaskStatus(id, Task.STATUS_SKIPPED)

  private def setTaskStatus(id: Long, status: Int) = Action {
    if (status < Task.STATUS_CREATED || status > Task.STATUS_DELETED) {
      Utils.badRequest(s"Invalid status [$status] provided.")
    } else {
      MPExceptionUtil.internalServerCatcher { () =>
        val statusUpdateJson = Json.obj("status" -> status)
        TaskDAL.update(statusUpdateJson)(id) match {
          case Some(resp) =>
            ActionManager.setAction(0, itemType.convertToItem(id), TaskStatusSet(status), "")
            NoContent
          case None => Utils.badRequest(s"Task with id [$id] not found to have status updated.")
        }
      }
    }
  }
}
