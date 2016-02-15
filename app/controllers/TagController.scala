package controllers

import com.fasterxml.jackson.databind.JsonMappingException
import org.maproulette.data.Tag
import org.maproulette.data.dal.TagDAL
import org.maproulette.utils.Utils
import play.api.libs.json._
import play.api.mvc.{Action, BodyParsers, Controller}

/**
  * @author cuthbertm
  */
object TagController extends Controller {
  def createTag() = Action(BodyParsers.parse.json) { implicit request =>
    val tagResult = Utils.insertJson(request.body).validate[Tag]
    tagResult.fold(
      errors => {
        BadRequest(Json.obj("status" -> "KO", "message" -> JsError.toJson(errors)))
      },
      tag => {
        try {
          Created(Json.toJson(TagDAL.insert(tag)))
        } catch {
          case e:Exception => InternalServerError(Json.obj("status" -> "KO", "message" -> e.getMessage))
        }
      }
    )
  }

  def updateTag(implicit id:Long) = Action(BodyParsers.parse.json) { implicit request =>
    try {
      Ok(Json.toJson(Tag.getUpdateOrCreateTag(request.body)))
    } catch {
      case e:JsonMappingException => BadRequest(Json.obj("status" -> "KO", "message" -> Json.parse(e.getMessage)))
      case e:Exception => InternalServerError(Json.obj("status" -> "KO", "message" -> e.getMessage))
    }
  }

  def getTag(id:Long) = Action { implicit request =>
    TagDAL.retrieveById(id) match {
      case Some(value) => Ok(Json.toJson(value))
      case None =>  NoContent
    }
  }

  def deleteTag(id:Long) = Action { implicit request =>
    TagDAL.delete(id)
    NoContent
  }

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
