package org.maproulette.controllers

import com.fasterxml.jackson.databind.JsonMappingException
import org.maproulette.actions.{Created => ActionCreated, _}
import org.maproulette.models.BaseObject
import org.maproulette.models.dal.BaseDAL
import org.maproulette.exception.MPExceptionUtil
import org.maproulette.session.SessionManager
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
  implicit val itemType:ItemType

  /**
    * Function can be implemented to extract more information than just the default create data,
    * to build other objects with the current object at the core. No data will be returned from this
    * function, it purely does work in the background AFTER creating the current object
    *
    * @param body The Json body of data
    * @param createdObject The object that was created by the create function
    */
  def extractAndCreate(body:JsValue, createdObject:T, userId:Long) : Unit = { }

  def create() = Action.async(BodyParsers.parse.json) { implicit request =>
    SessionManager.authenticatedRequest { implicit user =>
      val result = Utils.insertJsonID(request.body).validate[T]
      result.fold(
        errors => {
          BadRequest(Json.obj("status" -> "KO", "message" -> JsError.toJson(errors)))
        },
        element => {
          MPExceptionUtil.internalExceptionCatcher { () =>
            if (element.id < 0) {
              Created(Json.toJson(internalCreate(request.body, element, user.id)))
            } else {
              // if you provide the ID in the post method we will send you to the update path
              internalUpdate(request.body, user.id)(element.id) match {
                case Some(value) => Ok(Json.toJson(value))
                case None => NotModified
              }
            }
          }
        }
      )
    }
  }

  def internalCreate(requestBody:JsValue, element:T, userId:Long) : T = {
    DB.withTransaction { implicit c =>
      val createdObject = dal.insert(element)
      extractAndCreate(requestBody, createdObject, userId)
      ActionManager.setAction(userId, itemType.convertToItem(createdObject.id), ActionCreated(), "")
      createdObject
    }
  }

  /**
    * Similar to the extractAndCreate function however will be executed after the update function
    *
    * @param body
    */
  def extractAndUpdate(body:JsValue, updatedObject:Option[T], userId:Long) : Unit = {
    updatedObject match {
      case Some(updated) => extractAndCreate(body, updated, userId)
      case None => // ignore
    }
  }

  def update(implicit id:Long) = Action.async(BodyParsers.parse.json) { implicit request =>
    SessionManager.authenticatedRequest { implicit user =>
      try {
        internalUpdate(request.body, user.id) match {
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

  def internalUpdate(requestBody:JsValue, userId:Long)(implicit id:Long) : Option[T] = {
    DB.withTransaction { implicit c =>
      val updatedObject = dal.update(requestBody)
      extractAndUpdate(requestBody, updatedObject, userId)
      ActionManager.setAction(userId, itemType.convertToItem(id), Updated(), "")
      updatedObject
    }
  }

  def read(implicit id:Long) = Action.async { implicit request =>
    SessionManager.userAwareRequest { implicit user =>
      dal.retrieveById match {
        case Some(value) =>
          Ok(Json.toJson(value))
        case None =>
          NoContent
      }
    }
  }

  def delete(id:Long) = Action.async { implicit request =>
    SessionManager.authenticatedRequest { implicit user =>
      Ok(Json.obj("message" -> s"${dal.delete(id)} Tasks deleted by user ${user.id}."))
      ActionManager.setAction(user.id, itemType.convertToItem(id), Deleted(), "")
      NoContent
    }
  }

  def list(limit:Int, offset:Int) = Action.async { implicit request =>
    SessionManager.userAwareRequest { implicit user =>
      val result = dal.list(limit, offset)
      itemType match {
        case it:TaskType if user.isDefined =>
          result.foreach(task => ActionManager.setAction(user.get.id, itemType.convertToItem(task.id), TaskViewed(), ""))
        case _ => //ignore, only update view actions if it is a task type
      }
      Ok(Json.toJson(result))
    }
  }

  def batchUploadPost = batchUpload(false)

  def batchUploadPut = batchUpload(true)

  def batchUpload(update:Boolean) = Action.async(BodyParsers.parse.json) { implicit request =>
    SessionManager.authenticatedRequest { implicit user =>
      request.body.validate[List[JsValue]].fold(
        errors => {
          BadRequest(Json.obj("status" -> "KO", "message" -> JsError.toJson(errors)))
        },
        items => {
          internalBatchUpload(request.body, items, update, user.id)
          Ok(Json.obj("status" -> "OK", "message" -> "Items created and updated"))
        }
      )
    }
  }

  def internalBatchUpload(requestBody:JsValue, arr:List[JsValue], update:Boolean=false, userId:Long) : Unit = {
    arr.foreach(element => (element \ "id").asOpt[Long] match {
      case Some(itemID) => if (update) internalUpdate(element, userId)(itemID)
      case None => Utils.insertJsonID(element).validate[T].fold(
        errors => Logger.warn(s"Invalid json for type: ${JsError.toJson(errors).toString}"),
        validT => internalCreate(requestBody, validT, userId)
      )
    })
  }
}
