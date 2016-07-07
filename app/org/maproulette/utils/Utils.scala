// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.utils

import play.api.mvc.Results._
import play.api.libs.json._
import play.api.mvc.Result

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._


/**
  * Some random utliity helper functions
  *
  * @author cuthbertm
  */
object Utils extends DefaultWrites {

  /**
    * Checks to see if a string is a number
    *
    * @param x The string to check
    * @return true is string is a number
    */
  def isDigit(x:String) : Boolean = x forall Character.isDigit

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
  def insertJsonID(json:JsValue, overwrite:Boolean=false) : JsValue =
    insertIntoJson(json, "id", -1L)(LongWrites)

  def insertIntoJson[Value](json:JsValue, key:String, value:Value, overwrite:Boolean=false)
                           (implicit writes:Writes[Value]) : JsValue = {
    if (overwrite) {
      json.as[JsObject] + (key -> Json.toJson(value))
    } else {
      (json \ key).asOpt[Long] match {
        case Some(_) => json
        case None => json.as[JsObject] + (key -> Json.toJson(value))
      }
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

  def omitEmpty(original:JsObject, strings:Boolean=true, arrays:Boolean=true, objects:Boolean=true) : JsObject = {
    original.value.foldLeft(original) {
      case (obj, (key, JsString(st))) if strings & st.isEmpty => obj - key
      case (obj, (key, JsArray(arr))) if arrays & arr.isEmpty => obj - key
      case (obj, (key, JsObject(o))) if objects & o.isEmpty => obj - key
      case (obj, (_, _)) => obj
    }
  }
}
