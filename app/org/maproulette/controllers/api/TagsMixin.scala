package org.maproulette.controllers.api

import org.apache.commons.lang3.StringUtils
import org.maproulette.actions.{TagAdded, TagRemoved}
import org.maproulette.controllers.CRUDController
import org.maproulette.exception.MPExceptionUtil
import org.maproulette.models.dal.{TagDAL, TagDALMixin}
import org.maproulette.models.{BaseObject, Tag, Task}
import org.maproulette.session.User
import org.maproulette.utils.Utils
import play.api.libs.json.{JsDefined, JsUndefined, JsValue, Json}
import play.api.mvc.Action

/**
  * @author cuthbertm
  */
trait TagsMixin[T<:BaseObject[Long]] {

  this:CRUDController[T] =>

  def tagDAL:TagDAL
  def dalWithTags:TagDALMixin[T]

  /**
    * Gets tasks based on tags, this is regardless of the project or challenge parents.
    *
    * @param tags A comma separated list of tags to match against
    * @param limit The number of tasks to return
    * @param offset The paging offset, incrementing will take you to the next set in the list
    * @return The html Result containing a json array of the found tasks
    */
  def getItemsBasedOnTags(tags: String, limit: Int, offset: Int) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      if (StringUtils.isEmpty(tags)) {
        Utils.badRequest("A comma separated list of tags need to be provided via the query string. Example: ?tags=tag1,tag2")
      } else {
        Ok(Json.toJson(dalWithTags.getItemsBasedOnTags(tags.split(",").toList, limit, offset)))
      }
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
  def deleteTagsFromItem(id: Long, tags: String) = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      if (StringUtils.isEmpty(tags)) {
        Utils.badRequest("A comma separated list of tags need to be provided via the query string. Example: ?tags=tag1,tag2")
      } else {
        MPExceptionUtil.internalExceptionCatcher { () =>
          val tagList = tags.split(",").toList
          if (tagList.nonEmpty) {
            dalWithTags.deleteItemStringTags(id, tagList, user)
            actionManager.setAction(Some(user), itemType.convertToItem(id), TagRemoved(), tags)
          }
          NoContent
        }
      }
    }
  }

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
  def extractTags(body: JsValue, createdObject: T, user:User): Unit = {
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
    dalWithTags.updateItemTags(createdObject.id, tagIds, user)
    if (tagIds.nonEmpty) {
      actionManager.setAction(Some(user), itemType.convertToItem(createdObject.id), TagAdded(), tagIds.mkString(","))
    }
  }
}
