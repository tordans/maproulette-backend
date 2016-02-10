package org.maproulette.data

import anorm.SqlParser._
import anorm._
import org.maproulette.cache.TaskCacheManager
import play.api.Play.current
import play.api.db.DB
import play.api.libs.functional.syntax._
import play.api.libs.json._

/**
  * @author cuthbertm
  */
case class Task(override val id:Long=(-1),
                override val name: String,
                instruction: String,
                location: JsValue,
                primaryTag: Option[Long] = None,
                secondaryTag: Option[Long] = None,
                status:Int = 0) extends BaseObject {
  lazy val tags:List[String] = DB.withConnection { implicit c =>
    SQL"""SELECT t.name as name FROM tags as t
        INNER JOIN tags_on_tasks as tt ON t.id == tt.tag_id
        WHERE tt.task_id = $id""".as(str("name") *)
  }

  def getPrimaryTag = primaryTag match {
    case Some(id) => Tag.retrieveById(id)
    case None => None
  }

  def getSecondayTag = secondaryTag match {
    case Some(id) => Tag.retrieveById(id)
    case None => None
  }
}

object Task {
  implicit val caching = false
  implicit val manager = TaskCacheManager

  implicit val tagJsonReader = Tag.jsonReader
  implicit val tagJsonWriter = Tag.jsonWriter

  val jsonReader:Reads[Task] = (
      (JsPath \ "id").read[Long] and
      (JsPath \ "name").read[String] and
      (JsPath \ "instruction").read[String] and
      (JsPath \ "location").read[JsValue] and
      (JsPath \ "primaryTag").readNullable[Long] and
      (JsPath \ "secondaryTag").readNullable[Long] and
      (JsPath \ "status").read[Int]
    )(Task.apply _)

  val jsonWriter:Writes[Task] = (
      (JsPath \ "id").write[Long] and
      (JsPath \ "name").write[String] and
      (JsPath \ "instruction").write[String] and
      (JsPath \ "location").write[JsValue] and
      (JsPath \ "primaryTag").writeNullable[Long] and
      (JsPath \ "secondaryTag").writeNullable[Long] and
      (JsPath \ "status").write[Int]
  )(unlift(Task.unapply _))

  val parser: RowParser[Task] = {
      get[Long]("tasks.id") ~
      get[String]("tasks.name") ~
      get[String]("tasks.instruction") ~
      get[String]("location") ~
      get[Int]("tasks.status") map {
      case id ~ name ~ instruction ~ location ~ status =>
        new Task(id, name, instruction, Json.parse(location), None, None, status)
    }
  }

  def insert(task:Task) : Option[Task] = {
    manager.withOptionCaching { () =>
      DB.withTransaction { implicit c =>
        val identifier = SQL"""INSERT INTO tasks (name, location, instruction, status)
                     VALUES (${task.name},
                              ST_GeomFromGeoJSON(${task.location.toString}),
                              ${task.instruction},
                              ${task.status}
                     ) RETURNING id""".as(long("id") *).head
        // update the primary and seconday tags here
        Some(task.copy(id = identifier))
      }
    }
  }

  def update(id: Long, updates: JsValue): Option[Task] = {
    implicit val retriveId = id
    manager.withUpdatingCache(retrieveById) { implicit cached =>
      DB.withConnection { implicit c =>
        val name = (updates \ "name").asOpt[String] match {
          case Some(value) => value
          case None => cached.name
        }
        val instruction = (updates \ "instruction").asOpt[String] match {
          case Some(value) => value
          case None => cached.instruction
        }
        val location = (updates \ "location").asOpt[JsValue] match {
          case Some(value) => value.toString
          case None => cached.location.toString
        }
        val status = (updates \ "status").asOpt[Int] match {
          case Some(value) => value
          case None => cached.status
        }
        // need to allow for updates of primary and seconday tags
        Some(
          SQL"""UPDATE tasks SET name = $name, instruction = $instruction,
               location = ST_GeomFromGeoJSON($location),
               status = $status WHERE id = {id} RETURNING *""".as(parser *).head)
      }
    }
  }

  def retrieveById(id:Long): Option[Task] = {
    manager.withOptionCaching { () =>
      DB.withConnection { implicit c =>
        SQL"""SELECT t.id, t.name, t.instruction, ST_AsGeoJSON(t.location) as location,
            t1.name as primaryTag, t2.name as secondaryTag, t.status
        FROM tasks AS t
        LEFT JOIN tags t1 ON t1.id = t.primary_tag
        LEFT JOIN tags t2 ON t2.id = t.secondary_tag
        WHERE t.id = $id""".as(parser *).headOption
      }
    }
  }

  def retrieveByTag = ???

  def list(primaryTag:String, secondaryTag:Option[String]=None) : List[Task] = {
    DB.withConnection { implicit c =>
      val listingSQL = "SELECT t.id, t.name, t.instruction, ST_AsGeoJSON(t.location) as location, " +
        "t.primary_tag as primaryTag, t.secondary_tag as secondaryTag, t.status " +
        "FROM tasks AS t " +
        "LEFT JOIN tags t1 ON t1.id = t.primary_tag " +
        "LEFT JOIN tags t2 ON t2.id = t.secondary_tag " +
        "WHERE t1.name = {primaryTag}"

      val query = secondaryTag match {
        case Some(tag) =>
          (listingSQL + " AND t82.name = {secondaryTag}",
            Seq[NamedParameter]('primaryTag -> primaryTag, 'secondaryTag -> tag))
        case None => (listingSQL, Seq[NamedParameter]('primaryTag -> primaryTag))
      }
      SQL(query._1).on(query._2: _*).as(parser *)
    }
  }

  def delete(id:Long) : Int = {
    implicit val ids = List(id)
    manager.withCacheIDDeletion { () =>
      DB.withConnection { implicit c =>
        SQL"""DELETE FROM tasks WHERE id = $id""".executeUpdate()
      }
    }
  }

  /**
    * Links tags to a specific task. If the tags in the provided list do not exist then it will
    * create the new tags.
    *
    * @param task The task to update
    * @param tags The tags to be applied to the task
    */
  def updateTags(task:Task, tags:List[String]) : Unit = {
    DB.withTransaction { implicit c =>
      //val tagList = Tag.update(tags)
    }
  }
}
