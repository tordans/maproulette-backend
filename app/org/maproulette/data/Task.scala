package org.maproulette.data

import anorm._
import anorm.SqlParser._
import com.fasterxml.jackson.databind.JsonMappingException
import org.maproulette.data.dal.{ChallengeDAL, TaskDAL, TagDAL}
import play.api.Play.current
import play.api.db.DB
import play.api.libs.json._

/**
  * @author cuthbertm
  */
case class Task(override val id:Long,
                override val name: String,
                parent: Long,
                instruction: String,
                location: JsValue,
                status:Int = 0) extends BaseObject[Long] {
  lazy val tags:List[Tag] = DB.withConnection { implicit c =>
    SQL"""SELECT t.id, t.name, t.description FROM tags as t
        INNER JOIN tags_on_tasks as tt ON t.id == tt.tag_id
        WHERE tt.task_id = $id""".as(TagDAL.parser *)
  }

  def getParent = ChallengeDAL.retrieveById(parent)
}

object Task {
  implicit val taskReads: Reads[Task] = Json.reads[Task]
  implicit val taskWrites: Writes[Task] = Json.writes[Task]

  def getOrCreateTag(tag:Option[JsValue])(implicit id:Long) : Option[Long] = tag match {
    case Some(value) => Some(Tag.getUpdateOrCreateTag(value).id)
    case None => None
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
