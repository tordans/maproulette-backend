package org.maproulette.data.dal

import anorm._
import anorm.SqlParser._
import org.maproulette.cache.CacheManager
import org.maproulette.data.{Tag, Task}
import play.api.db.DB
import play.api.libs.json._
import play.api.Play.current

import scala.collection.mutable.ListBuffer

/**
  * @author cuthbertm
  */
object TaskDAL extends BaseDAL[Long, Task] {
  override val cacheManager = new CacheManager[Long, Task]()
  override val tableName: String = "tasks"
  override val retrieveColumns:String = "tasks.id, tasks.name, tasks.identifier, tasks.parent_id, " +
    "tasks.instruction, ST_AsGeoJSON(tasks.location) AS location, tasks.status"

  implicit val parser: RowParser[Task] = {
    get[Long]("tasks.id") ~
      get[String]("tasks.name") ~
      get[Option[String]]("tasks.identifier") ~
      get[Long]("parent_id") ~
      get[String]("tasks.instruction") ~
      get[String]("location") ~
      get[Option[Int]]("tasks.status") map {
      case id ~ name ~ identifier ~ parent_id ~ instruction ~ location ~ status =>
        new Task(id, name, identifier, parent_id, instruction, Json.parse(location), status)
    }
  }

  override def insert(task:Task) : Task = {
    cacheManager.withOptionCaching { () =>
      DB.withTransaction { implicit c =>
        val newTaskId = SQL"""INSERT INTO tasks (name, identifier, parent_id, location, instruction, status)
                     VALUES (${task.name}, ${task.identifier}, ${task.parent},
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
      DB.withTransaction { implicit c =>
        val name = (value \ "name").asOpt[String].getOrElse(cachedItem.name)
        val identifier = (value \ "identifier").asOpt[String].getOrElse(cachedItem.identifier.getOrElse(""))
        val parentId = (value \ "parentId").asOpt[Long].getOrElse(cachedItem.parent)
        val instruction = (value \ "instruction").asOpt[String].getOrElse(cachedItem.instruction)
        val location = (value \ "location").asOpt[JsValue].getOrElse(cachedItem.location)
        val status = (value \ "status").asOpt[Int].getOrElse(cachedItem.status.getOrElse(0))

        SQL"""UPDATE tasks SET name = $name, identifier = $identifier, parent_id = $parentId,
              instruction = $instruction, location = ST_GeomFromGeoJSON(${location.toString}),
              status = $status
              WHERE id = $id""".executeUpdate()

        Some(Task(id, name, Some(identifier), parentId, instruction, location, Some(status)))
      }
    }
  }

  def updateTaskTags(taskId:Long, tags:List[Long]) : Unit = {
    if (tags.nonEmpty) {
      DB.withTransaction { implicit c =>
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
  }

  def deleteTaskTags(taskId:Long, tags:List[Long]) : Unit = {
    if (tags.nonEmpty) {
      DB.withTransaction { implicit c =>
        SQL"""DELETE FROM tags_on_tasks WHERE task_id = {$taskId} AND tag_id IN ($tags)""".execute()
      }
    }
  }

  def deleteTaskStringTags(taskId:Long, tags:List[String]) : Unit = {
    if (tags.nonEmpty) {
      val lowerTags = tags.map(_.toLowerCase)
      DB.withTransaction { implicit c =>
        SQL"""DELETE FROM tags_on_tasks tt USING tags t
              WHERE tt.tag_id = t.id AND
                    tt.task_id = $taskId AND
                    t.name IN ($lowerTags)""".execute()
      }
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
    val lowerTags = tags.map(_.toLowerCase)
    DB.withConnection { implicit c =>
      val sqlLimit = if (limit == -1) "ALL" else limit+""
      val query = s"SELECT $retrieveColumns FROM tasks " +
        "INNER JOIN tags_on_tasks tt ON tasks.id = tt.task_id " +
        "INNER JOIN tags tg ON tg.id = tt.tag_id " +
        "WHERE tg.name IN ({tags}) " +
        s"LIMIT $sqlLimit OFFSET {offset}"
      SQL(query).on('tags -> ParameterValue.toParameterValue(lowerTags), 'offset -> offset).as(parser *)
    }
  }

  def getRandomTasksStr(projectId:Option[Long],
                     challengeId:Option[Long],
                     tags:List[String],
                     limit:Int=(-1)) : List[Task] = {
    if (tags.isEmpty) {
      getRandomTasksInt(projectId, challengeId, List(), limit)
    } else {
      val idList = TagDAL.retrieveListByName(tags.map(_.toLowerCase)).map(_.id)
      getRandomTasksInt(projectId, challengeId, idList, limit)
    }
  }

  def getRandomTasksInt(projectId:Option[Long],
                     challengeId:Option[Long],
                     tags:List[Long],
                     limit:Int=(-1)) : List[Task] = {
    val sqlLimit = if (limit == -1) "ALL" else limit+""
    val firstQuery = s"SELECT $retrieveColumns FROM tasks "
    val secondQuery = "SELECT COUNT(*) FROM tasks "

    val parameters = new ListBuffer[NamedParameter]()
    val queryBuilder = new StringBuilder
    val whereClause = new StringBuilder
    challengeId match {
      case Some(id) =>
        whereClause ++= "t.parent_id = {parentId} "
        parameters += ('parentId -> ParameterValue.toParameterValue(id))
      case None => //ignore
    }
    projectId match {
      case Some(id) =>
        queryBuilder ++= "INNER JOIN challenges c ON c.id = tasks.parent_id " +
                          "INNER JOIN projects p ON p.id = c.parent_id "
        if (whereClause.nonEmpty) {
          whereClause ++= "AND "
        }
        whereClause ++= "p.id = {projectId} "
        parameters += ('projectId -> ParameterValue.toParameterValue(id))
      case None => //ignore
    }
    if (tags.nonEmpty) {
      queryBuilder ++= "INNER JOIN tags_on_tasks tt ON tt.task_id = tasks.id "
      if (whereClause.nonEmpty) {
        whereClause ++= "AND "
      }
      whereClause ++= "tt.tag_id IN ({tagids}) "
      parameters += ('tagids -> ParameterValue.toParameterValue(tags))
    }
    if (whereClause.nonEmpty) {
      whereClause.insert(0, "WHERE ")
    }
    val query = s"$firstQuery ${queryBuilder.toString} ${whereClause.toString} " +
                s"OFFSET FLOOR(RANDOM()*(" +
                s"$secondQuery ${queryBuilder.toString} ${whereClause.toString}" +
                s")) LIMIT $sqlLimit"

    implicit val ids = List[Long]()
    cacheManager.withIDListCaching { implicit cachedItems =>
      DB.withConnection { implicit c =>
        if (parameters.nonEmpty) {
          SQL(query).on(parameters:_*).as(parser *)
        } else {
          SQL(query).as(parser *)
        }
      }
    }
  }
}
