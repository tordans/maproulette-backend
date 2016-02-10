package controllers

import org.maproulette.data.Tag
import play.api.libs.json.{Writes, Reads, JsError, Json}
import play.api.mvc.{Action, BodyParsers, Controller}

/**
  * @author cuthbertm
  */
object TagController extends Controller {

  implicit val tagReads: Reads[Tag] = Tag.jsonReader
  implicit val tagWrites: Writes[Tag] = Tag.jsonWriter

  def createTag() = Action(BodyParsers.parse.json) { implicit request =>
    val tagResult = request.body.validate[Tag]
    tagResult.fold(
      errors => {
        BadRequest(Json.obj("status" -> "KO", "message" -> JsError.toJson(errors)))
      },
      tag => {
        try {
          Created(Json.toJson(Tag.insert(tag)))
        } catch {
          case e:Exception => InternalServerError(Json.obj("status" -> "KO", "message" -> e.getMessage))
        }
      }
    )
  }

  def updateTag(id:Long) = Action(BodyParsers.parse.json) { implicit request =>
    Tag.update(id, request.body) match {
      case Some(value) => Ok(Json.toJson(value))
      case None => NoContent
    }
  }

  def getTag(id:Long) = Action { implicit request =>
    Tag.retrieveById(id) match {
      case Some(value) => Ok(Json.toJson(value))
      case None =>  NoContent
    }
  }

  def deleteTag(id:Long) = Action { implicit request =>
    Tag.delete(id)
    NoContent
  }

  def getTags(prefix:String, limit:Int, offset:Int) = Action { implicit request =>
    Ok(Json.toJson(Tag.retrieveListByPrefix(prefix, limit, offset)))
  }
}
