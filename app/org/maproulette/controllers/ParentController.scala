package org.maproulette.controllers

import org.maproulette.data.BaseObject
import org.maproulette.data.dal.ParentDAL
import play.api.libs.json._
import play.api.mvc.Action

/**
  * @author cuthbertm
  */
trait ParentController[T<:BaseObject[Long], C<:BaseObject[Long]] extends CRUDController[T] {
  override protected val dal: ParentDAL[Long, T, C]
  override implicit val tReads: Reads[T]
  override implicit val tWrites: Writes[T]
  protected val cWrites:Writes[C]

  def childrenAddition(children:List[C]) = {
    implicit val writes:Writes[C] = cWrites
    (__).json.update(
      __.read[JsObject].map{ o => o ++ Json.obj("children" -> Json.toJson(children)) }
    )
  }

  def listChildren(id:Long, limit:Int, offset:Int) = Action {
    implicit val writes:Writes[C] = cWrites
    try {
      Ok(Json.toJson(dal.listChildren(limit, offset)(id)))
    } catch {
      case e:Exception => InternalServerError(Json.obj("status" -> "KO", "message" -> e.getMessage))
    }
  }

  def expandedList(id:Long, limit:Int, offset:Int) = Action {
    implicit val writes:Writes[C] = cWrites
    try {
      // first get the parent
      val parent = Json.toJson(dal.retrieveById(id))
      // now list the children
      val children = dal.listChildren(limit, offset)(id)
      // now replace the parent field in the parent with a children array
      parent.transform(childrenAddition(children)) match {
        case JsSuccess(value, _) => Ok(value)
        case JsError(errors) => InternalServerError(Json.obj("status" -> "KO", "message" -> JsError.toJson(errors)))
      }
    } catch {
      case e:Exception => InternalServerError(Json.obj("status" -> "KO", "message" -> e.getMessage))
    }
  }
}
