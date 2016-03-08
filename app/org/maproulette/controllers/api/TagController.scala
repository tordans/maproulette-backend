package org.maproulette.controllers.api

import javax.inject.Inject

import org.maproulette.actions.TagType
import org.maproulette.controllers.CRUDController
import org.maproulette.models.Tag
import org.maproulette.models.dal.{BaseDAL, TagDAL}
import org.maproulette.session.SessionManager
import org.maproulette.utils.Utils
import play.api.libs.json._
import play.api.mvc.Action

/**
  * @author cuthbertm
  */
class TagController @Inject() extends CRUDController[Tag] {
  override protected val dal: BaseDAL[Long, Tag] = TagDAL
  override implicit val tReads: Reads[Tag] = Tag.tagReads
  override implicit val tWrites: Writes[Tag] = Tag.tagWrites
  override implicit val itemType = TagType()

  def getTags(prefix:String, limit:Int, offset:Int) = Action.async { implicit request =>
    SessionManager.userAwareRequest { implicit user =>
      Ok(Json.toJson(TagDAL.retrieveListByPrefix(prefix, limit, offset)))
    }
  }

  override def internalBatchUpload(requestBody: JsValue, arr: List[JsValue], update: Boolean, userId:Long): Unit = {
    val tagList = arr.flatMap(element => (element \ "id").asOpt[Long] match {
      case Some(itemID) => if (update) element.validate[Tag].fold(
        errors => None,
        value => Some(value)
      ) else None
      case None => Utils.insertJsonID(element).validate[Tag].fold(
        errors => None,
        value => Some(value)
      )
    })
    TagDAL.updateTagList(tagList)
  }
}
