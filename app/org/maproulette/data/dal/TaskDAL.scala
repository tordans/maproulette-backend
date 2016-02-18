package org.maproulette.data.dal

import anorm._
import anorm.SqlParser._
import org.maproulette.cache.CacheManager
import org.maproulette.data.{Tag, Task}
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
  override val retrieveColumns:String = "tasks.id, tasks.name, tasks.parent_id, " +
    "tasks.instruction, ST_AsGeoJSON(tasks.location) AS location, tasks.status"

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

  def retrieveByTag(tag:String) : List[Task] = {
    List.empty
  }

  def updateTaskTags(taskId:Long, tags:List[Long]) : Unit = {
    DB.withConnection { implicit c =>
      val indexedValues = tags.zipWithIndex
      val rows = indexedValues.map{ case (value, i) =>
        s"({taskid_$i}, {tagid_$i})"
      }.mkString(",")
      val parameters = indexedValues.flatMap{ case(value, i) =>
        Seq(
          NamedParameter(s"taskid_$i", ParameterValue.toParameterValue(taskId)),
          NamedParameter(s"tagid_$i", ParameterValue.toParameterValue(value))
        )
      }

      SQL("INSERT INTO tags_on_tasks (task_id, tag_id) VALUES " + rows)
        .on(parameters: _*)
          .execute()
    }
  }

  /**
    * Links tags to a specific task. If the tags in the provided list do not exist then it will
    * create the new tags.
    *
    * @param taskId The task id to update with
    * @param tags The tags to be applied to the task
    */
  def updateTaskTagNames(taskId:Long, tags:List[String]) : Unit = {
    val tagIds = tags.flatMap { tag => {
      TagDAL.retrieveByName(tag) match {
        case Some(t) => Some(t.id)
        case None => Some(TagDAL.insert(Tag(-1, tag)).id)
      }
    }}
    updateTaskTags(taskId, tagIds)
  }

  def getTasksBasedOnTags(tags:List[String], limit:Int, offset:Int) : List[Task] = {
    DB.withConnection { implicit c =>
      val sqlLimit = if (limit == -1) "ALL" else limit+""
      val query = s"SELECT $retrieveColumns FROM tasks " +
        "INNER JOIN tags_on_tasks tt ON tasks.id = tt.task_id " +
        "INNER JOIN tags tg ON tg.id = tt.tag_id " +
        "WHERE tg.name IN ({tags}) " +
        s"LIMIT $sqlLimit OFFSET {offset}"
      SQL(query).on('tags -> ParameterValue.toParameterValue(tags), 'offset -> offset).as(parser *)
    }
  }

  def getRandomTasksStr(projectId:Option[Long],
                     challengeId:Option[Long],
                     tags:List[String],
                     limit:Int=(-1)) : List[Task] = {
    List.empty
  }

  def getRandomTasksInt(projectId:Option[Long],
                     challengeId:Option[Long],
                     tags:List[Long],
                     limit:Int=(-1)) : List[Task] = {
    /*val sqlLimit = if (limit == -1) "ALL" else limit+""
    val sqlTuple = projectId match {
      case Some(id) => (
          "INNER JOIN challenges c ON c.id = t.parent_id",
          "({projectId} = -1 OR c.parent_id = {projectId)"
        )
      case None => ("", "")
    }
    val query = "SELECT * FROM tasks t" +
                s"${sqlTuple._1} " +
                s"WHERE ${sqlTuple._2} " +
                      "({challengeId} = -1 OR t.parent_id = {challenge_id}) " +
                s"OFFSET FLOOR(RANDOM()*(SELECT COUNT(*) FROM tasks)) LIMIT $sqlLimit"
    SQL(query).on('challengeId -> )*/
    List.empty
  }
}
