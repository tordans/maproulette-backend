package org.maproulette.utils

import play.api.libs.json.{Json, JsObject, JsValue}

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
  def getDefaultOption[T](obj:Option[T], default:T) = obj match {
    case Some(value) => value
    case None => default
  }

  /**
    * Quick method that will include a -1 id. -1 is the long value that is used for an object
    * that has not been inserted into the database yet. Will not add if id is already there
    *
    * @param json The json that you want to add the id into
    * @return
    */
  def insertJson(json:JsValue) : JsValue = {
    (json \ "id").asOpt[Long] match {
      case Some(_) => json
      case None => json.as[JsObject] + ("id" -> Json.toJson(-1))
    }
  }
}
