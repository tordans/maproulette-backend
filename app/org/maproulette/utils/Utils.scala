package org.maproulette.utils

import play.api.mvc.Results._
import play.api.libs.json.{Json, JsObject, JsValue}
import play.api.mvc.Result
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._


/**
  * Some random utliity helper functions
  *
  * @author cuthbertm
  */
object Utils {

  /**
    * Wraps a JSON message inside of a BadRequest response
    *
    * @param message The message to place inside the json
    * @return 400 BadRequest
    */
  def badRequest(message:String) : Result = {
    BadRequest(Json.obj("status" -> "KO", "message" -> message))
  }

  /**
    * Quick method that will include a -1 id. -1 is the long value that is used for an object
    * that has not been inserted into the database yet. Will not add if id is already there
    *
    * @param json The json that you want to add the id into
    * @return
    */
  def insertJsonID(json:JsValue) : JsValue = {
    (json \ "id").asOpt[Long] match {
      case Some(_) => json
      case None => json.as[JsObject] + ("id" -> Json.toJson(-1))
    }
  }

  def getCaseClassVariableNames[T:TypeTag] : List[MethodSymbol] = {
    typeOf[T].members.collect {
      case m:MethodSymbol if m.isCaseAccessor => m
    }.toList
  }

  def getCaseClassValueMappings[T:ClassTag](obj:T)(implicit tag:TypeTag[T]) : Map[MethodSymbol, Any] = {
    implicit val mirror = scala.reflect.runtime.currentMirror
    val im = mirror.reflect(obj)
    typeOf[T].members.collect {
      case m:MethodSymbol if m.isCaseAccessor => m -> im.reflectMethod(m).apply()
    }.toMap
  }
}
