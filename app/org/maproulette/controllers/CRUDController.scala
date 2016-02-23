package org.maproulette.controllers

import com.fasterxml.jackson.databind.JsonMappingException
import org.maproulette.data.BaseObject
import org.maproulette.data.dal.BaseDAL
import org.maproulette.utils.Utils
import play.api.Logger
import play.api.db.DB
import play.api.libs.json._
import play.api.mvc.{Action, BodyParsers, Controller}
import play.api.Play.current

/**
  * @author cuthbertm
  */
trait CRUDController[T<:BaseObject[Long]] extends Controller {
  protected val dal:BaseDAL[Long, T]
  implicit val tReads:Reads[T]
  implicit val tWrites:Writes[T]

  /**
    * Function can be implemented to extract more information than just the default create data,
    * to build other objects with the current object at the core. No data will be returned from this
    * function, it purely does work in the background AFTER creating the current object
    *
    * @param body The Json body of data
    * @param createdObject The object that was created by the create function
    */
  def extractAndCreate(body:JsValue, createdObject:T) : Unit = { }

  def create() = Action(BodyParsers.parse.json) { implicit request =>
    val result = Utils.insertJsonID(request.body).validate[T]
    result.fold(
      errors => {
        BadRequest(Json.obj("status" -> "KO", "message" -> JsError.toJson(errors)))
      },
      element => {
        Utils.internalServerCatcher { () =>
          if (element.id < 0) {
            Created(Json.toJson(internalCreate(request.body, element)))
          } else {
            // if you provide the ID in the post method we will send you to the update path
            internalUpdate(request.body)(element.id) match {
              case Some(value) => Ok(Json.toJson(value))
              case None => NotModified
            }
          }
        }
      }
    )
  }

  def internalCreate(requestBody:JsValue, element:T) : T = {
    DB.withTransaction { implicit c =>
      val createdObject = dal.insert(element)
      extractAndCreate(requestBody, createdObject)
      createdObject
    }
  }

  /**
    * Similar to the extractAndCreate function however will be executed after the update function
    *
    * @param body
    */
  def extractAndUpdate(body:JsValue, updatedObject:Option[T]) : Unit = {
    updatedObject match {
      case Some(updated) => extractAndCreate(body, updated)
      case None => // ignore
    }
  }

  def update(implicit id:Long) = Action(BodyParsers.parse.json) { implicit request =>
    Utils.internalServerCatcher { () =>
      try {
        internalUpdate(request.body) match {
          case Some(value) => Ok(Json.toJson(value))
          case None => NotModified
        }
      } catch {
        case e:JsonMappingException =>
          Logger.error(e.getMessage, e)
          BadRequest(Json.obj("status" -> "KO", "message" -> Json.parse(e.getMessage)))
      }
    }
  }

  def internalUpdate(requestBody:JsValue)(implicit id:Long) : Option[T] = {
    DB.withTransaction { implicit c =>
      val updatedObject = dal.update(requestBody)
      extractAndUpdate(requestBody, updatedObject)
      updatedObject
    }
  }

  def read(implicit id:Long) = Action {
    Utils.internalServerCatcher { () =>
      dal.retrieveById match {
        case Some(value) =>
          Ok(Json.toJson(value))
        case None =>
          NoContent
      }
    }
  }

  def delete(id:Long) = Action {
    Utils.internalServerCatcher { () =>
      Ok(Json.obj("message" -> s"${dal.delete(id)} Tasks deleted."))
      NoContent
    }
  }

  def list(limit:Int, offset:Int) = Action {
    Utils.internalServerCatcher{ () =>
      Ok(Json.toJson(dal.list(limit, offset)))
    }
  }

  def batchUploadPost = batchUpload(false)

  def batchUploadPut = batchUpload(true)

  def batchUpload(update:Boolean) = Action(BodyParsers.parse.json) { implicit request =>
    request.body.validate[List[JsValue]].fold(
      errors => {
        BadRequest(Json.obj("status" -> "KO", "message" -> JsError.toJson(errors)))
      },
      items => {
        internalBatchUpload(request.body, items, update)
        Ok(Json.obj("status" -> "OK", "message" -> "Items created and updated"))
      }
    )
  }

  def internalBatchUpload(requestBody:JsValue, arr:List[JsValue], update:Boolean=false) : Unit = {
    arr.foreach(element => (element \ "id").asOpt[Long] match {
      case Some(itemID) => if (update) internalUpdate(element)(itemID)
      case None => Utils.insertJsonID(element).validate[T].fold(
        errors => Logger.warn(s"Invalid json for type: ${JsError.toJson(errors).toString}"),
        validT => internalCreate(requestBody, validT)
      )
    })
  }
}
