package org.maproulette.models.dal

import java.sql.Connection
import javax.inject.{Inject, Singleton}

import anorm._
import anorm.SqlParser._
import org.apache.commons.lang3.StringUtils
import org.maproulette.cache.CacheManager
import org.maproulette.models.{Tag, Task}
import org.maproulette.exception.InvalidException
import org.maproulette.models.utils.DALHelper
import org.maproulette.session.User
import play.api.db.Database
import play.api.libs.json._

import scala.collection.mutable.ListBuffer

/**
  * The data access layer for the Task objects
  *
  * @author cuthbertm
  */
@Singleton
class TaskDAL @Inject() (override val db:Database, tagDAL: TagDAL)
    extends BaseDAL[Long, Task] with DALHelper {
  // The cache manager for that tasks
  override val cacheManager = new CacheManager[Long, Task]()
  // The database table name for the tasks
  override val tableName: String = "tasks"
  // The columns to be retrieved for the task. Reason this is required is because one of the columns
  // "tasks.location" is a PostGIS object in the database and we want it returned in GeoJSON instead
  // so the ST_AsGeoJSON function is used to convert it to geoJSON
  override val retrieveColumns:String = "*, ST_AsGeoJSON(tasks.location) AS location"

  // The anorm row parser to convert records from the task table to task objects
  implicit val parser: RowParser[Task] = {
    get[Long]("tasks.id") ~
      get[String]("tasks.name") ~
      get[Option[String]]("tasks.identifier") ~
      get[Long]("parent_id") ~
      get[String]("tasks.instruction") ~
      get[Option[String]]("location") ~
      get[Option[Int]]("tasks.status") map {
      case id ~ name ~ identifier ~ parent_id ~ instruction ~ location ~ status =>
        Task(id, name, identifier, parent_id, instruction, location, getTaskGeometries(id), status)
    }
  }

  /**
    * Retrieve all the geometries for the task
    *
    * @param id Id for the task
    * @return A feature collection geojson of all the task geometries
    */
  private def getTaskGeometries(id:Long) : String = {
    db.withTransaction { implicit c =>
      SQL"""SELECT row_to_json(fc)::text as geometries
            FROM ( SELECT 'FeatureCollection' As type, array_to_json(array_agg(f)) As features
                   FROM ( SELECT 'Feature' As type,
                                  ST_AsGeoJSON(lg.geom)::json As geometry,
                                  hstore_to_json(lg.properties) As properties
                          FROM task_geometries As lg
                          WHERE task_id = $id
                    ) As f
            )  As fc""".as(SqlParser.str("geometries").single)
    }
  }

  /**
    * Inserts a new task object into the database
    *
    * @param task The task to be inserted into the database
    * @param user The user executing the task
    * @return The object that was inserted into the database. This will include the newly created id
    */
  override def insert(task:Task, user:User) : Task = {
    task.hasWriteAccess(user)
    cacheManager.withOptionCaching { () =>
      db.withTransaction { implicit c =>
        var parameters = Seq(
          NamedParameter("name", ParameterValue.toParameterValue(task.name)),
          NamedParameter("identifier", ParameterValue.toParameterValue(task.identifier)),
          NamedParameter("parent", ParameterValue.toParameterValue(task.parent)),
          NamedParameter("instruction", ParameterValue.toParameterValue(task.instruction))
        )
        val locationValue = if (!task.location.isDefined || StringUtils.isEmpty(task.location.get)) {
          ("", "")
        } else {
          parameters = parameters :+ NamedParameter("location", ParameterValue.toParameterValue(task.location.get.toString))
          ("location,", s"ST_SetSRID(ST_GeomFromGeoJSON({location}),4326),")
        }
        // status is ignored on insert and always set to CREATED
        val query = s"""INSERT INTO tasks (name, identifier, parent_id, ${locationValue._1} instruction, status)
                        VALUES ({name}, {identifier}, {parent}, ${locationValue._2} {instruction}, ${Task.STATUS_CREATED}
                        ) RETURNING id"""
        val newTaskId = SQL(query).on(parameters: _*).as(long("id").*).head
        updateGeometries(newTaskId, Json.parse(task.geometries))
        Some(task.copy(id = newTaskId))
      }
    }.get
  }

  /**
    * Updates a task object in the database.
    *
    * @param value A json object containing fields to be updated for the task
    * @param user The user executing the task
    * @param id The id of the object that you are updating
    * @return An optional object, it will return None if no object found with a matching id that was supplied
    */
  override def update(value:JsValue, user:User)(implicit id:Long): Option[Task] = {
    cacheManager.withUpdatingCache(Long => retrieveById) { implicit cachedItem =>
      cachedItem.hasWriteAccess(user)
      db.withTransaction { implicit c =>
        val name = (value \ "name").asOpt[String].getOrElse(cachedItem.name)
        val identifier = (value \ "identifier").asOpt[String].getOrElse(cachedItem.identifier.getOrElse(""))
        val parentId = (value \ "parentId").asOpt[Long].getOrElse(cachedItem.parent)
        val instruction = (value \ "instruction").asOpt[String].getOrElse(cachedItem.instruction)
        val location = (value \ "location").asOpt[String].getOrElse(cachedItem.location.getOrElse(""))
        val status = (value \ "status").asOpt[Int].getOrElse(cachedItem.status.getOrElse(0))
        if (!Task.isValidStatusProgression(cachedItem.status.getOrElse(0), status)) {
          throw new InvalidException(s"Could not set status for task [$id], " +
            s"progression from ${cachedItem.status.getOrElse(0)} to $status not valid.")
        }
        val geometries = (value \ "geometries").asOpt[String].getOrElse(cachedItem.geometries)

        var parameters = Seq(
          NamedParameter("name", ParameterValue.toParameterValue(name)),
          NamedParameter("identifier", ParameterValue.toParameterValue(identifier)),
          NamedParameter("parentId", ParameterValue.toParameterValue(parentId)),
          NamedParameter("instruction", ParameterValue.toParameterValue(instruction)),
          NamedParameter("status", ParameterValue.toParameterValue(status)),
          NamedParameter("id", ParameterValue.toParameterValue(id))
        )
        val locationInfo = if (StringUtils.isEmpty(location)) {
          ""
        } else {
          parameters = parameters :+ NamedParameter("location", location)
          "location = ST_GeomFromGeoJSON({location}),"
        }

        val query = s"""UPDATE tasks SET name = {name}, identifier = {identifier}, parent_id = {parentId},
                          instruction = {instruction}, $locationInfo status = {status}
                        WHERE id = {id}"""

        SQL(query).on(parameters: _*).executeUpdate()
        updateGeometries(id, Json.parse(geometries))
        Some(Task(id, name, Some(identifier), parentId, instruction, Some(location), geometries, Some(status)))
      }
    }
  }

  /**
    * Function that updates the geometries for the task, either during an insert or update
    *
    * @param taskId The task Id to update the geometries for
    * @param value The geojson that contains the geometries/features
    * @param setLocation Whether to set the location based on the geometries or not
    */
  private def updateGeometries(taskId:Long, value:JsValue, setLocation:Boolean=false)(implicit c:Connection) : Unit = {
    SQL"""DELETE FROM task_geometries WHERE task_id = $taskId""".executeUpdate()
    val features = (value \ "features").as[List[JsValue]]
    val indexedValues = features.zipWithIndex
    val rows = indexedValues.map {
      case (_, i) => s"({taskId}, ST_SetSRID(ST_GeomFromGeoJSON({geom_$i}),4326), {props_$i}::hstore)"
    }.mkString(",")
    val parameters = indexedValues.flatMap { case (featureJson, i) =>
      val geometry = ()
      val props = (featureJson \ "properties").asOpt[JsValue] match {
        case Some(p) => p.as[Map[String, String]].map(v => s""""${v._1}"=>"${v._2}"""").mkString(",")
        case None => ""
      }
      Seq(
        NamedParameter(s"geom_$i", ParameterValue.toParameterValue((featureJson \ "geometry").as[JsValue].toString)),
        NamedParameter(s"props_$i", ParameterValue.toParameterValue(props))
      )
    } ++ Seq(NamedParameter("taskId", ParameterValue.toParameterValue(taskId)))
    SQL("INSERT INTO task_geometries (task_id, geom, properties) VALUES " + rows)
      .on(parameters: _*)
      .execute()
  }

  /**
    * Updates the tags on the task. This maps the tag objects to the task objects in the database
    * through the use of a mapping table
    *
    * @param taskId The id of the task to add the tags too
    * @param tags A list of tags to add to the task
    * @param user The user executing the task
    */
  def updateTaskTags(taskId:Long, tags:List[Long], user:User) : Unit = {
    retrieveById(taskId) match {
      case Some(task) =>
        task.hasWriteAccess(user)
        if (tags.nonEmpty) {
          db.withTransaction { implicit c =>
            val indexedValues = tags.zipWithIndex
            val rows = indexedValues.map{ case (_, i) =>
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
      case None =>
        throw new InvalidException(s"""Could not add tags [${tags.mkString(",")}]. Task [$taskId] Not Found.""")
    }
  }

  /**
    * Deletes tags from a task. This will not delete any tasks or tags, it will simply sever the
    * connection between the task and tag.
    *
    * @param taskId The task id that the user is removing the tags from
    * @param tags The tags that are being removed from the task
    * @param user The user executing the task
    */
  def deleteTaskTags(taskId:Long, tags:List[Long], user:User) : Unit = {
    if (tags.nonEmpty) {
      db.withTransaction { implicit c =>
        SQL"""DELETE FROM tags_on_tasks WHERE task_id = {$taskId} AND tag_id IN ($tags)""".execute()
      }
    }
  }

  /**
    * Pretty much the same as {@link this#deleteTaskTags} but removes the tags from the task based
    * on the name of the tag instead of the id. This is most likely to be used more often.
    *
    * @param taskId The id of the task that is having the tags remove from it
    * @param tags The tags to be removed from the task
    * @param user The user executing the task
    */
  def deleteTaskStringTags(taskId:Long, tags:List[String], user:User) : Unit = {
    if (tags.nonEmpty) {
      val lowerTags = tags.map(_.toLowerCase)
      db.withTransaction { implicit c =>
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
    * @param user The user executing the task
    */
  def updateTaskTagNames(taskId:Long, tags:List[String], user:User) : Unit = {
    val tagIds = tags.flatMap { tag => {
      tagDAL.retrieveByName(tag) match {
        case Some(t) => Some(t.id)
        case None => Some(tagDAL.insert(Tag(-1, tag), user).id)
      }
    }}
    updateTaskTags(taskId, tagIds, user)
  }

  /**
    * Get a list of tasks based purely on the tags that are associated with the tasks
    *
    * @param tags The list of tags to match
    * @param limit The number of tasks to return
    * @param offset For paging, where 0 is the first page
    * @return A list of tags that have the tags
    */
  def getTasksBasedOnTags(tags:List[String], limit:Int, offset:Int) : List[Task] = {
    val lowerTags = tags.map(_.toLowerCase)
    db.withConnection { implicit c =>
      val sqlLimit = if (limit == -1) "ALL" else limit+""
      val query = s"""SELECT $retrieveColumns FROM tasks
                      INNER JOIN challenges c ON c.id = tasks.parent_id
                      INNER JOIN projects p ON p.id = c.parent_id
                      INNER JOIN tags_on_tasks tt ON tasks.id = tt.task_id
                      INNER JOIN tags tg ON tg.id = tt.tag_id
                      WHERE c.enabled = TRUE AND tg.name IN ({tags})
                      LIMIT $sqlLimit OFFSET {offset}"""
      SQL(query).on('tags -> ParameterValue.toParameterValue(lowerTags), 'offset -> offset).as(parser.*)
    }
  }

  /**
    * Gets a random task based on the tags, and can include project and challenge restrictions as well.
    *
    * @param projectId None if ignoring project restriction, otherwise the id of the project to limit
    *                  where the random tasks come from
    * @param challengeId None if ignoring the challenge restriction, otherwise the id of the challenge
    *                    to limit where the random tasks come from
    * @param tags List of tag names that will restrict the returned tags
    * @param limit The amount of tags that should be returned
    * @return A list of random tags matching the above criteria, an empty list if none match
    */
  def getRandomTasksStr(projectId:Option[Long],
                     challengeId:Option[Long],
                     tags:List[String],
                     limit:Int=(-1)) : List[Task] = {
    if (tags.isEmpty) {
      getRandomTasksInt(projectId, challengeId, List(), limit)
    } else {
      val idList = tagDAL.retrieveListByName(tags.map(_.toLowerCase)).map(_.id)
      getRandomTasksInt(projectId, challengeId, idList, limit)
    }
  }

  /**
    * Gets a random task based on the tags, and can include project and challenge restrictions as well.
    * The sql query that is built to execute this could be costly on larger datasets, the reason being is
    * that it will basically execute the same query twice, once to retrieve the objects that match
    * the criteria and the second time to get an accurate random offset to choose a random tasks from
    * all the matching tasks in the set. This will most likely always be called with a limit of 1.
    *
    * @param projectId None if ignoring project restriction, otherwise the id of the project to limit
    *                  where the random tasks come from
    * @param challengeId None if ignoring the challenge restriction, otherwise the id of the challenge
    *                    to limit where the random tasks come from
    * @param tags List of tag ids that will restrict the returned tags
    * @param limit The amount of tags that should be returned
    * @return A list of random tags matching the above criteria, an empty list if none match
    */
  def getRandomTasksInt(projectId:Option[Long],
                     challengeId:Option[Long],
                     tags:List[Long],
                     limit:Int=(-1)) : List[Task] = {
    val firstQuery =
      s"""SELECT $retrieveColumns FROM tasks
          INNER JOIN challenges c ON c.id = tasks.parent_id
          INNER JOIN projects p ON p.id = c.parent_id
       """.stripMargin
    val secondQuery =
      """SELECT COUNT(*) FROM tasks
         INNER JOIN challenges c ON c.id = tasks.parent_id
         INNER JOIN projects p ON p.id = c.parent_id
      """.stripMargin

    val parameters = new ListBuffer[NamedParameter]()
    val queryBuilder = new StringBuilder
    val whereClause = new StringBuilder("WHERE c.enabled = TRUE AND p.enabled = TRUE")

    challengeId match {
      case Some(id) =>
        whereClause ++= " AND tasks.parent_id = {parentId} "
        parameters += ('parentId -> ParameterValue.toParameterValue(id))
      case None => //ignore
    }
    projectId match {
      case Some(id) =>
        whereClause ++= " AND p.id = {projectId} "
        parameters += ('projectId -> ParameterValue.toParameterValue(id))
      case None => //ignore
    }
    if (tags.nonEmpty) {
      queryBuilder ++= "INNER JOIN tags_on_tasks tt ON tt.task_id = tasks.id "
      whereClause ++= " AND tt.tag_id IN ({tagids}) "
      parameters += ('tagids -> ParameterValue.toParameterValue(tags))
    }
    val query = s"$firstQuery ${queryBuilder.toString} ${whereClause.toString} " +
                s"OFFSET FLOOR(RANDOM()*(" +
                s"$secondQuery ${queryBuilder.toString} ${whereClause.toString}" +
                s")) LIMIT ${sqlLimit(limit)}"

    implicit val ids = List[Long]()
    cacheManager.withIDListCaching { implicit cachedItems =>
      db.withConnection { implicit c =>
        if (parameters.nonEmpty) {
          SQL(query).on(parameters:_*).as(parser.*)
        } else {
          SQL(query).as(parser.*)
        }
      }
    }
  }
}
