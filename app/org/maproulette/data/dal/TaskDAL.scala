package org.maproulette.data.dal

import anorm._
import anorm.SqlParser._
import org.maproulette.cache.TaskCacheManager
import org.maproulette.data.{Tag, Task}
import org.maproulette.utils.Utils
import play.api.db.DB
import play.api.libs.json._
import play.api.Play.current

/**
  * @author cuthbertm
  */
object TaskDAL extends BaseDAL[Task] {
  override implicit val cacheManager = TaskCacheManager
  override implicit val tableName: String = "tasks"
  override implicit val retrieveColumns:String = "id, name, instruction, " +
    "ST_AsGeoJSON(location) AS location, primary_tag, secondary_tag, status"

  implicit val parser: RowParser[Task] = {
    get[Long]("tasks.id") ~
      get[String]("tasks.name") ~
      get[String]("tasks.instruction") ~
      get[String]("location") ~
      get[Option[Long]]("primary_tag") ~
      get[Option[Long]]("secondary_tag") ~
      get[Int]("tasks.status") map {
      case id ~ name ~ instruction ~ location ~ primaryTag ~ secondaryTag ~ status =>
        new Task(id, name, instruction, Json.parse(location), primaryTag, secondaryTag, status)
    }
  }

  def insert(task:Task) : Task = {
    cacheManager.withOptionCaching { () =>
      DB.withTransaction { implicit c =>
        val newTaskId = SQL"""INSERT INTO tasks (name, location, instruction, status)
                     VALUES (${task.name},
                              ST_GeomFromGeoJSON(${task.location.toString}),
                              ${task.instruction},
                              ${task.status}
                     ) RETURNING id""".as(long("id") *).head
        Some(task.copy(id = newTaskId))
      }
    }.get
  }

  def update(value:JsValue)(implicit id:Long): Option[Task] = {
    cacheManager.withUpdatingCache(Long => retrieveById) { implicit cachedItem =>
      DB.withConnection { implicit c =>
        val name = Utils.getDefaultOption((value \ "name").asOpt[String], cachedItem.name)
        val instruction = Utils.getDefaultOption((value \ "instruction").asOpt[String], cachedItem.instruction)
        val location = Utils.getDefaultOption((value \ "location").asOpt[JsValue], cachedItem.location)
        val status = Utils.getDefaultOption((value \ "status").asOpt[Int], cachedItem.status)
        val primaryTagId = Task.getOrCreateTag((value \ "primaryTag").asOpt[JsValue])
        val secondaryTagId = Task.getOrCreateTag((value \ "secondaryTag").asOpt[JsValue])

        SQL"""UPDATE tasks SET name = ${name}, instruction = ${instruction},
              location = ST_GeomFromGeoJSON(${location.toString}), status = ${status},
              primary_tag = ${primaryTagId}, secondary_tag = ${secondaryTagId}
              WHERE id = $id""".executeUpdate()

        Some(Task(id, name, instruction, location, primaryTagId, secondaryTagId, status))
      }
    }
  }

  def retrieveByTag = ???

  def list(primaryTag:Option[String]=None, secondaryTag:Option[String]=None,
           limit: Int = 10, offset: Int = 0) : List[Task] = {
    DB.withConnection { implicit c =>
      val second = primaryTag match {
        case Some(_) => secondaryTag
        case None => None
      }
      SQL"""SELECT t.id, t.name, t.instruction, ST_AsGeoJSON(t.location) as location,
                    t.primary_tag, t.secondary_tag, t.status
            FROM tasks AS t
            LEFT JOIN tags t1 ON t1.id = t.primary_tag
            LEFT JOIN tags t2 ON t2.id = t.secondary_tag
            WHERE ($primaryTag IS NULL OR t1.name = $primaryTag) AND
                  ($second IS NULL OR t2.name = $second)
            LIMIT $limit OFFSET $offset"""
        .as(parser *)
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
