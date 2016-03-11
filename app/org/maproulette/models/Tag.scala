package org.maproulette.models

import com.fasterxml.jackson.databind.JsonMappingException
import org.maproulette.models.dal.TagDAL
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
               description: Option[String] = None) extends BaseObject[Long]

object Tag {
  implicit val tagWrites: Writes[Tag] = Json.writes[Tag]
  implicit val tagReads: Reads[Tag] = Json.reads[Tag]

  def getUpdateOrCreateTag(value:JsValue)(implicit id:Long) : Tag = {
    TagDAL.update(value) match {
      case Some(tag) => tag
      case None => tagReads.reads(value).fold(
        errors => throw new JsonMappingException(JsError.toJson(errors).toString),
        newTag => TagDAL.insert(newTag)
      )
    }
  }
}
