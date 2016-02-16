package org.maproulette.controllers

import com.fasterxml.jackson.databind.JsonMappingException
import org.maproulette.data.BaseObject
import org.maproulette.data.dal.BaseDAL
import org.maproulette.utils.Utils
import play.api.libs.json.{JsError, Json, Reads, Writes}
import play.api.mvc.{Action, BodyParsers, Controller}

/**
  * @author cuthbertm
  */
trait CRUDController[T<:BaseObject[Long]] extends Controller {
  protected val dal:BaseDAL[Long, T]
  implicit val tReads:Reads[T]
  implicit val tWrites:Writes[T]

  def create() = Action(BodyParsers.parse.json) { implicit request =>
    val result = Utils.insertJson(request.body).validate[T]
    result.fold(
      errors => {
        BadRequest(Json.obj("status" -> "KO", "message" -> JsError.toJson(errors)))
      },
      element => {
        try {
          Created(Json.toJson(dal.insert(element)))
        } catch {
          case e:Exception => InternalServerError(Json.obj("status" -> "KO", "message" -> e.getMessage))
        }
      }
    )
  }

  def update(implicit id:Long) = Action(BodyParsers.parse.json) { implicit request =>
    try {
      Ok(Json.toJson(dal.update(request.body)))
    } catch {
      case e:JsonMappingException => BadRequest(Json.obj("status" -> "KO", "message" -> Json.parse(e.getMessage)))
      case e:Exception => InternalServerError(Json.obj("status" -> "KO", "message" -> e.getMessage))
    }
  }

  def read(implicit id:Long) = Action {
    dal.retrieveById match {
      case Some(value) =>
        Ok(Json.toJson(value))
      case None =>
        NoContent
    }
  }

  def delete(id:Long) = Action {
    Ok(Json.obj("message" -> s"${dal.delete(id)} Tasks deleted."))
    NoContent
  }

  def list(limit:Int, offset:Int) = Action {
    try {
      Ok(Json.toJson(dal.list(limit, offset)))
    } catch {
      case e:Exception => InternalServerError(Json.obj("status" -> "KO", "message" -> e.getMessage))
    }
  }
}
