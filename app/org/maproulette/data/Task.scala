package org.maproulette.data

import anorm._
import anorm.SqlParser._
import com.fasterxml.jackson.databind.JsonMappingException
import org.maproulette.data.dal.{TaskDAL, TagDAL}
import org.maproulette.utils.Utils
import play.api.Play.current
import play.api.db.DB
import play.api.libs.json._
import play.api.libs.functional.syntax._

/**
  * @author cuthbertm
  */
case class Task(override val id:Long,
                override val name: String,
                instruction: String,
                location: JsValue,
                primaryTag: Option[Long] = None,
                secondaryTag: Option[Long] = None,
                status:Int = 0) extends BaseObject {
  lazy val tags:List[Tag] = DB.withConnection { implicit c =>
    SQL"""SELECT t.id, t.name, t.description FROM tags as t
        INNER JOIN tags_on_tasks as tt ON t.id == tt.tag_id
        WHERE tt.task_id = $id""".as(TagDAL.parser *)
  }

  def getPrimaryTag = primaryTag match {
    case Some(id) => TagDAL.retrieveById(id)
    case None => None
  }

  def getSecondayTag = secondaryTag match {
    case Some(id) => TagDAL.retrieveById(id)
    case None => None
  }
}

object Task {
  implicit val taskReads: Reads[Task] = (
    (JsPath \ "id").read[Long] and
    (JsPath \ "name").read[String] and
      (JsPath \ "instruction").read[String] and
      (JsPath \ "location").read[JsValue] and
      (JsPath \ "primaryTag").readNullable[JsValue] and
      (JsPath \ "secondaryTag").readNullable[JsValue] and
      (JsPath \ "status").readNullable[Int]
    )(Task.insertReads _)

  def insertReads(id:Long, name:String, instruction:String, location:JsValue, primaryTag:Option[JsValue],
                  secondaryTag:Option[JsValue], status:Option[Int]) =
    Task(id,
      name,
      instruction,
      location,
      getOrCreateTag(primaryTag)(-1),
      getOrCreateTag(secondaryTag)(-1),
      Utils.getDefaultOption(status, 0)
    )

  def getOrCreateTag(tag:Option[JsValue])(implicit id:Long) : Option[Long] = tag match {
    case Some(value) => Some(Tag.getUpdateOrCreateTag(value).id)
    case None => None
  }

  implicit val taskWrites:Writes[Task] = (
    (JsPath \ "id").write[Long] and
      (JsPath \ "name").write[String] and
      (JsPath \ "instruction").write[String] and
      (JsPath \ "location").write[JsValue] and
      (JsPath \ "primaryTag").writeNullable[Tag] and
      (JsPath \ "secondaryTag").writeNullable[Tag] and
      (JsPath \ "status").write[Int]
  )(unlift(Task.jsonWrite _))

  def jsonWrite(task:Task) = {
    Some((task.id, task.name, task.instruction, task.location, task.getPrimaryTag, task.getSecondayTag, task.status))
  }

  def getUpdateOrCreateTask(value:JsValue)(implicit id:Long) : Task = {
    TaskDAL.update(value) match {
      case Some(task) => task
      case None => taskReads.reads(value).fold(
        errors => throw new JsonMappingException(JsError.toJson(errors).toString),
        newTask => TaskDAL.insert(newTask)
      )
    }
  }
}
