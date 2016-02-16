package controllers

import org.maproulette.controllers.CRUDController
import org.maproulette.data.Tag
import org.maproulette.data.dal.{BaseDAL, TagDAL}
import play.api.libs.json._
import play.api.mvc.{Action, BodyParsers}

/**
  * @author cuthbertm
  */
object TagController extends CRUDController[Tag] {
  override protected val dal: BaseDAL[Long, Tag] = TagDAL
  override implicit val tReads: Reads[Tag] = Tag.tagReads
  override implicit val tWrites: Writes[Tag] = Tag.tagWrites

  def getTags(prefix:String, limit:Int, offset:Int) = Action { implicit request =>
    Ok(Json.toJson(TagDAL.retrieveListByPrefix(prefix, limit, offset)))
  }

  def buildTags() = Action(BodyParsers.parse.json) { implicit request =>
    request.body.validate[List[Tag]].fold(
      errors => {
        BadRequest(Json.obj("status" -> "KO", "message" -> JsError.toJson(errors)))
      },
      tagArray => {
        Ok(Json.toJson(TagDAL.updateTagList(tagArray)))
      }
    )
  }
}
