package org.maproulette.models

import javax.inject.Inject

import com.fasterxml.jackson.databind.JsonMappingException
import org.maproulette.models.dal.TagDAL
import org.maproulette.session.User
import play.api.libs.json._

/**
  * Tags sit outside of the object hierarchy and have no parent or children objects associated it.
  * It simply has a many to one mapping between tags and tasks. This allows tasks to be easily
  * searched for and organized. Helping people find tasks related to what interests them.
  *
  * @author cuthbertm
  */
case class Tag(override val id: Long,
               override val name: String,
               override val description: Option[String] = None) extends BaseObject[Long]

object Tag {
  implicit val tagWrites: Writes[Tag] = Json.writes[Tag]
  implicit val tagReads: Reads[Tag] = Json.reads[Tag]

  @Inject val tagDAL:TagDAL = null

  /**
    * Update a tag, or if it does not exist, then create a new tag
    *
    * @param value The json value containing the updates
    * @param user The user executing the request
    * @param id id of the tag you are updating
    * @return The updated or newly created tag
    */
  def getUpdateOrCreateTag(value:JsValue, user:User)(implicit id:Long) : Tag = {
    tagDAL.update(value, user) match {
      case Some(tag) => tag
      case None => tagReads.reads(value).fold(
        errors => throw new JsonMappingException(JsError.toJson(errors).toString),
        newTag => tagDAL.insert(newTag, user)
      )
    }
  }
}
