package org.maproulette.data.dal

import anorm._
import anorm.SqlParser._
import org.maproulette.cache.CacheManager
import org.maproulette.data.Task
import org.maproulette.utils.Utils
import play.api.db.DB
import play.api.libs.json._
import play.api.Play.current

/**
  * @author cuthbertm
  */
object TaskDAL extends BaseDAL[Long, Task] {
  override val cacheManager = new CacheManager[Long, Task]()
  override val tableName: String = "tasks"
  override val retrieveColumns:String = "id, name, parent_id, instruction, " +
    "ST_AsGeoJSON(location) AS location, status"

  implicit val parser: RowParser[Task] = {
    get[Long]("tasks.id") ~
      get[String]("tasks.name") ~
      get[Long]("parent_id") ~
      get[String]("tasks.instruction") ~
      get[String]("location") ~
      get[Int]("tasks.status") map {
      case id ~ name ~ parent_id ~ instruction ~ location ~ status =>
        new Task(id, name, parent_id, instruction, Json.parse(location), status)
    }
  }

  override def insert(task:Task) : Task = {
    cacheManager.withOptionCaching { () =>
      DB.withTransaction { implicit c =>
        val newTaskId = SQL"""INSERT INTO tasks (name, parent_id, location, instruction, status)
                     VALUES (${task.name}, ${task.parent},
                              ST_GeomFromGeoJSON(${task.location.toString}),
                              ${task.instruction},
                              ${task.status}
                     ) RETURNING id""".as(long("id") *).head
        Some(task.copy(id = newTaskId))
      }
    }.get
  }

  override def update(value:JsValue)(implicit id:Long): Option[Task] = {
    cacheManager.withUpdatingCache(Long => retrieveById) { implicit cachedItem =>
      DB.withConnection { implicit c =>
        val name = Utils.getDefaultOption((value \ "name").asOpt[String], cachedItem.name)
        val parentId = Utils.getDefaultOption((value \ "parentId").asOpt[Long], cachedItem.parent)
        val instruction = Utils.getDefaultOption((value \ "instruction").asOpt[String], cachedItem.instruction)
        val location = Utils.getDefaultOption((value \ "location").asOpt[JsValue], cachedItem.location)
        val status = Utils.getDefaultOption((value \ "status").asOpt[Int], cachedItem.status)

        SQL"""UPDATE tasks SET name = ${name}, parent_id = ${parentId},
              instruction = ${instruction}, location = ST_GeomFromGeoJSON(${location.toString}),
              status = ${status}
              WHERE id = $id""".executeUpdate()

        Some(Task(id, name, parentId, instruction, location, status))
      }
    }
  }

  def retrieveByTag = ???

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
