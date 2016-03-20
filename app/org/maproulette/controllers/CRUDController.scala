package org.maproulette.controllers

import com.fasterxml.jackson.databind.JsonMappingException
import org.maproulette.actions.{Created => ActionCreated, _}
import org.maproulette.models.BaseObject
import org.maproulette.models.dal.BaseDAL
import org.maproulette.exception.MPExceptionUtil
import org.maproulette.session.{User, SessionManager}
import org.maproulette.utils.Utils
import play.api.Logger
import play.api.db.DB
import play.api.libs.json._
import play.api.mvc.{Action, BodyParsers, Controller}
import play.api.Play.current

/**
  * This is the base controller class that handles all the CRUD operations for the objects in Map Roulette.
  * This includes creation, reading, updating and deleting. It also includes a standard list function
  * and batch upload process.
  *
  * @author cuthbertm
  */
trait CRUDController[T<:BaseObject[Long]] extends Controller {
  // Data access layer that has to be instantiated by the class that mixes in the trait
  protected val dal:BaseDAL[Long, T]
  // The default reads that allows the class to read the json from a posted json body
  implicit val tReads:Reads[T]
  // The default writes that allows the class to write the object as json to a response body
  implicit val tWrites:Writes[T]
  // the type of object that the controller is executing against
  implicit val itemType:ItemType
  // The session manager which should be injected into the implementing class using @Inject
  val sessionManager:SessionManager
  // The action manager which should be injected into the implementing class using @Inject
  val actionManager:ActionManager

  /**
    * Function can be implemented to extract more information than just the default create data,
    * to build other objects with the current object at the core. No data will be returned from this
    * function, it purely does work in the background AFTER creating the current object
    *
    * @param body The Json body of data
    * @param createdObject The object that was created by the create function
    * @param user The user that is executing the function
    */
  def extractAndCreate(body:JsValue, createdObject:T, user:User) : Unit = { }

  /**
    * The base create function that most controllers will run through to create the object. The
    * actual work will be passed on to the internalCreate function. This is so that if you want
    * to create your object differently you can keep the standard http functionality around and
    * not have to reproduce a lot of the work done in this function.
    * Must be authenticated to perform operation
    *
    * @return 201 Created with the json body of the created object
    */
  def create() = Action.async(BodyParsers.parse.json) { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      val result = Utils.insertJsonID(request.body).validate[T]
      result.fold(
        errors => {
          BadRequest(Json.obj("status" -> "KO", "message" -> JsError.toJson(errors)))
        },
        element => {
          MPExceptionUtil.internalExceptionCatcher { () =>
            if (element.id < 0) {
              Created(Json.toJson(internalCreate(request.body, element, user)))
            } else {
              // if you provide the ID in the post method we will send you to the update path
              internalUpdate(request.body, user)(element.id) match {
                case Some(value) => Ok(Json.toJson(value))
                case None => NotModified
              }
            }
          }
        }
      )
    }
  }

  /**
    * Calls the insert function from the data access layer for the particular object. It will also
    * call the extractAndCreate function after the insert, which by default does nothing. The ParentController
    * will use that function to create any children of the parent. Will also create a "create" action
    * in the database
    *
    * @param requestBody The request body containing the full json payload
    * @param element The intial object to be created. Ie. if this was the ProjectController then it would be a project object
    * @param user The user that is executing this request
    * @return The createdObject (not any of it's children if creating multiple objects, only top level)
    */
  def internalCreate(requestBody:JsValue, element:T, user:User) : T = {
    val createdObject = dal.insert(element, user)
    extractAndCreate(requestBody, createdObject, user)
    actionManager.setAction(Some(user), itemType.convertToItem(createdObject.id), ActionCreated(), "")
    createdObject
  }

  /**
    * Passes functionality to the extractAndCreate function if the updatedObject is not None,
    * otherwise will do nothing by default
    *
    * @param body The request body containing the full json payload
    * @param updatedObject The object that was updated.
    * @param user The user executing the operation
    */
  def extractAndUpdate(body:JsValue, updatedObject:Option[T], user:User) : Unit = {
    updatedObject match {
      case Some(updated) => extractAndCreate(body, updated, user)
      case None => // ignore
    }
  }

  /**
    * Base update function for the object. The update function works very similarly to the create
    * function. It does however allow the user to supply only the elements that are needed to updated.
    * Must be authenticated to perform operation
    *
    * @param id The id for the object
    * @return 200 OK with the updated object, 304 NotModified if not updated
    */
  def update(implicit id:Long) = Action.async(BodyParsers.parse.json) { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      try {
        internalUpdate(request.body, user) match {
          case Some(value) => Ok(Json.toJson(value))
          case None =>  NotModified
        }
      } catch {
        case e:JsonMappingException =>
          Logger.error(e.getMessage, e)
          BadRequest(Json.obj("status" -> "KO", "message" -> Json.parse(e.getMessage)))
      }
    }
  }

  /**
    * The function that does the actual update of the object, it will pass of the updatedObject to
    * the extractAndUpdate function after the object has been updated.
    *
    * @param requestBody The full request body payload pass in the request
    * @param user The user executing the request
    * @param id The id of the object being updated
    * @return The object that was updated, None if it was not updated.
    */
  def internalUpdate(requestBody:JsValue, user:User)(implicit id:Long) : Option[T] = {
    val updatedObject = dal.update(requestBody, user)
    extractAndUpdate(requestBody, updatedObject, user)
    actionManager.setAction(Some(user), itemType.convertToItem(id), Updated(), "")
    updatedObject
  }

  /**
    * Retrieves the object from the database or primary storage and writes it as json as a response.
    *
    * @param id The id of the object that is being retrieved
    * @return 200 Ok, 204 NoContent if not found
    */
  def read(implicit id:Long) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      dal.retrieveById match {
        case Some(value) =>
          Ok(Json.toJson(value))
        case None =>
          NoContent
      }
    }
  }

  /**
    * Deletes an object from the database or primary storage.
    * Must be authenticated to perform operation
    *
    * @param id The id of the object to delete
    * @return 204 NoContent
    */
  def delete(id:Long) = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      Ok(Json.obj("message" -> s"${dal.delete(id, user)} Tasks deleted by user ${user.id}."))
      actionManager.setAction(Some(user), itemType.convertToItem(id), Deleted(), "")
      NoContent
    }
  }

  /**
    * Simply lists the objects for the current controller. This can be an especially expensive operation,
    * as if you just list all tasks it will try to list all of the tasks, which could be over 100,000
    * which could cause OOM's but also cause issues with the browser trying to displaying all the data.
    *
    * TODO: This function probably should force authentication, but also use streaming. Possibly
    * only allow super users to do it.
    *
    * @param limit limit the number of objects returned in the list
    * @param offset For paging, if limit is 10, total 100, then offset 1 will return items 11 - 20
    * @return A list of requested objects
    */
  def list(limit:Int, offset:Int) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      val result = dal.list(limit, offset)
      itemType match {
        case it:TaskType if user.isDefined =>
          result.foreach(task => actionManager.setAction(user, itemType.convertToItem(task.id), TaskViewed(), ""))
        case _ => //ignore, only update view actions if it is a task type
      }
      Ok(Json.toJson(result))
    }
  }

  /**
    * Helper function that does a batch upload and only creates new object, does not update existing ones
    * Must be authenticated to perform operation
    *
    * @return 200 OK basic message saying all items where uploaded
    */
  def batchUploadPost = batchUpload(false)

  /**
    * Helper function that does a batch upload and creates new objects, and updates existing ones.
    * Must be authenticated to perform operation
    *
    * @return 200 OK basic message saying all items where uploaded
    */
  def batchUploadPut = batchUpload(true)

  /**
    * Allows a basic upload batch process of the items from the json payload. This is also leveraged
    * when creating children objects at the same time as the parent.
    * Must be authenticated to perform operation
    *
    * @param update Whether to update any objects found matching in the database
    * @return 200 OK basic message saying all items where uploaded
    */
  def batchUpload(update:Boolean) = Action.async(BodyParsers.parse.json) { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      request.body.validate[List[JsValue]].fold(
        errors => {
          BadRequest(Json.obj("status" -> "KO", "message" -> JsError.toJson(errors)))
        },
        items => {
          internalBatchUpload(request.body, items, user, update)
          Ok(Json.obj("status" -> "OK", "message" -> "Items created and updated"))
        }
      )
    }
  }

  /**
    * Internal method that is used to actually execute the batch upload
    *
    * @param requestBody The full request body
    * @param arr The array of json objects representing the objects or values of those objects that you want to update/create
    * @param user The user executing the request
    * @param update Whether to update the object if a matching object is found, if false will simply do nothing
    */
  def internalBatchUpload(requestBody:JsValue, arr:List[JsValue], user:User, update:Boolean=false) : Unit = {
    arr.foreach(element => (element \ "id").asOpt[Long] match {
      case Some(itemID) => if (update) internalUpdate(element, user)(itemID)
      case None => Utils.insertJsonID(element).validate[T].fold(
        errors => Logger.warn(s"Invalid json for type: ${JsError.toJson(errors).toString}"),
        validT => internalCreate(requestBody, validT, user)
      )
    })
  }
}
