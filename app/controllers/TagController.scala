package controllers

import org.maproulette.controllers.CRUDController
import org.maproulette.data.Tag
import org.maproulette.data.dal.{BaseDAL, TagDAL}
import org.maproulette.utils.Utils
import play.api.libs.json._
import play.api.mvc.Action

/**
  * @author cuthbertm
  */
object TagController extends CRUDController[Tag] {
  override protected val dal: BaseDAL[Long, Tag] = TagDAL
  override implicit val tReads: Reads[Tag] = Tag.tagReads
  override implicit val tWrites: Writes[Tag] = Tag.tagWrites

  def getTags(prefix:String, limit:Int, offset:Int) = Action { implicit request =>
    Utils.internalServerCatcher { () =>
      Ok(Json.toJson(TagDAL.retrieveListByPrefix(prefix, limit, offset)))
    }
  }

  override def internalBatchUpload(requestBody: JsValue, arr: List[JsValue], update: Boolean): Unit = {
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
