package org.maproulette.utils

import play.api.libs.json.{Json, JsObject, JsValue}
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._


/**
  * @author cuthbertm
  */
object Utils {

  /**
    * Based on an option object, will either return the value for the option or a supplied value
    *
    * @param obj The option that you are checking
    * @param default The default value if the option is None
    * @tparam T The type of object in the option
    * @return the value of the option of the default value
    */
  def getDefaultOption[T](obj:Option[T], default:T) : T = obj match {
    case Some(value) => value
    case None => default
  }

  def getDefaultOption[T](obj:Option[T], default:Option[T], secondDefault:T) : T = default match {
    case Some(value) => getDefaultOption(obj, value)
    case None => getDefaultOption(obj, secondDefault)
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
