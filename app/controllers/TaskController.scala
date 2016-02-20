package controllers

import org.maproulette.controllers.CRUDController
import org.maproulette.data.{Tag, Task}
import org.maproulette.data.dal.{TagDAL, TaskDAL}
import play.api.Logger
import play.api.libs.json._
import play.api.mvc.{BodyParsers, Action}

/**
  * @author cuthbertm
  */
object TaskController extends CRUDController[Task] {
  override protected val dal = TaskDAL
  override implicit val tReads: Reads[Task] = Task.taskReads
  override implicit val tWrites: Writes[Task] = Task.taskWrites
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
  }

  def getTagsForTask(implicit id: Long) = Action {
    Ok(Json.toJson(Task(id, "", -1, "", Json.parse("{}")).tags))
  }

  def getTasksBasedOnTags(tags: String, limit: Int, offset: Int) = Action { implicit request =>
    try {
      Ok(Json.toJson(dal.getTasksBasedOnTags(tags.split(",").toList, limit, offset)))
    } catch {
      case e: Exception =>
        Logger.error(e.getMessage, e)
        InternalServerError(Json.obj("status" -> "KO", "message" -> e.getMessage))
    }
  }

  def getRandomTasks(tags: String,
                     limit:Int) = Action {
    try {
      Ok(Json.toJson(dal.getRandomTasksStr(None, None, tags.split(",").toList, limit)))
    } catch {
      case e: Exception =>
        Logger.error(e.getMessage, e)
        InternalServerError(Json.obj("status" -> "KO", "message" -> e.getMessage))
    }
  }

  def batchUpload() = Action(BodyParsers.parse.json) { implicit request =>
    Ok("")
  }
}
