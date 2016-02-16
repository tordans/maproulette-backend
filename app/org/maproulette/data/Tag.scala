package org.maproulette.data

import com.fasterxml.jackson.databind.JsonMappingException
import org.maproulette.data.dal.TagDAL
import play.api.libs.json._

/**
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
