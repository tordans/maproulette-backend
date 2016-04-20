package org.maproulette.models.dal

import java.sql.Connection
import javax.inject.{Inject, Singleton}

import anorm._
import anorm.SqlParser._
import org.apache.commons.lang3.StringUtils
import org.maproulette.actions.TaskType
import org.maproulette.cache.CacheManager
import org.maproulette.models.{Lock, Task}
import org.maproulette.exception.{InvalidException, NotFoundException}
import org.maproulette.models.utils.DALHelper
import org.maproulette.session.{SearchParameters, User}
import play.api.db.Database
import play.api.libs.json._

import scala.collection.mutable.ListBuffer

/**
  * The data access layer for the Task objects
  *
  * @author cuthbertm
  */
@Singleton
class TaskDAL @Inject() (override val db:Database, override val tagDAL: TagDAL)
    extends BaseDAL[Long, Task] with DALHelper with TagDALMixin[Task] {
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
      get[Long]("parent_id") ~
      get[String]("tasks.instruction") ~
      get[Option[String]]("location") ~
      get[Option[Int]]("tasks.status") map {
      case id ~ name ~ parent_id ~ instruction ~ location ~ status =>
        Task(id, name, parent_id, instruction, location, getTaskGeometries(id), status)
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
        val query = s"""INSERT INTO tasks (name, parent_id, ${locationValue._1} instruction, status)
                        VALUES ({name}, {parent}, ${locationValue._2} {instruction}, ${Task.STATUS_CREATED}
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
          NamedParameter("parentId", ParameterValue.toParameterValue(parentId)),
          NamedParameter("instruction", ParameterValue.toParameterValue(instruction)),
          NamedParameter("status", ParameterValue.toParameterValue(status)),
          NamedParameter("id", ParameterValue.toParameterValue(id))
        )
        val locationInfo = if (StringUtils.isEmpty(location)) {
          ""
        } else {
          parameters = parameters :+ NamedParameter("location", location)
          "location = ST_SetSRID(ST_GeomFromGeoJSON({location}),4326),"
        }

        val query = s"""UPDATE tasks SET name = {name}, parent_id = {parentId},
                          instruction = {instruction}, $locationInfo status = {status}
                        WHERE id = {id}"""

        SQL(query).on(parameters: _*).executeUpdate()
        updateGeometries(id, Json.parse(geometries))
        Some(Task(id, name, parentId, instruction, Some(location), geometries, Some(status)))
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
    * Sets the task for a given user. The user cannot set the status of a task unless the object has
    * been locked by the same user before hand.
    * Will throw an InvalidException if the task status cannot be set due to the current task status
    * Will throw an IllegalAccessException if the user is a guest user, or if the task is locked by
    * a different user.
    *
    * @param task The task to set the status for
    * @param status The status to set
    * @param user The user setting the status
    * @return The number of rows updated, should only ever be 1
    */
  def setTaskStatus(task:Task, status:Int, user:User) : Int = {
    if (!Task.isValidStatusProgression(task.status.getOrElse(Task.STATUS_CREATED), status)) {
      throw new InvalidException("Invalid task status supplied.")
    } else if (user.guest) {
      throw new IllegalAccessException("Guest users cannot make edits to tasks.")
    }

    db.withTransaction { implicit c =>
      val updatedRows = SQL"""UPDATE tasks t SET status = $status
          FROM tasks t2
          LEFT JOIN locked l ON l.item_id = t2.id AND l.item_type = ${task.itemType} AND l.user_id = ${user.id}
          WHERE l.item_id = ${task.id} AND t.id = ${task.id}""".executeUpdate()
      // if returning 0, then this is because the item is locked by a different user
      if (updatedRows == 0) {
        throw new IllegalAccessException(s"Current task [${task.id} is locked by another user, cannot update status at this time.")
      }
      // if you set the status successfully on a task you will lose the lock of that task
      unlockItem(user, task)
      updatedRows
    }
  }

  /**
    * Simple query to retrieve the next task in the sequence
    *
    * @param parentId The parent of the task
    * @param currentTaskId The current task that we are basing our query from
    * @return An optional task, if no more tasks in the list will retrieve the first task
    */
  def getNextTaskInSequence(parentId:Long, currentTaskId:Long) : Option[(Task, Lock)] = {
    db.withConnection { implicit c =>
      val lp = for {
        task <- parser
        lock <- lockedParser
      } yield task -> lock
      SQL"""SELECT * FROM tasks
            INNER JOIN locked ON locked.item_id = tasks.id
            WHERE id > $currentTaskId AND parent_id = $parentId
            ORDER BY id ASC LIMIT 1""".as(lp.*).headOption match {
        case Some(t) => Some(t)
        case None =>
          SQL"""SELECT * FROM tasks WHERE parent_id = $parentId
                INNER JOIN locked ON locked.item_id = tasks.id
                ORDER BY id ASC LIMIT 1""".as(lp.*).headOption
      }
    }
  }

  /**
    * Simple query to retrieve the previous task in the sequence
    *
    * @param parentId The parent of the task
    * @param currentTaskId The current task that we are basing our query from
    * @return An optional task, if no more tasks in the list will retrieve the last task
    */
  def getPreviousTaskInSequence(parentId:Long, currentTaskId:Long) : Option[(Task, Lock)] = {
    db.withConnection { implicit c =>
      val lp = for {
        task <- parser
        lock <- lockedParser
      } yield task -> lock
      SQL"""SELECT * FROM tasks
            INNER JOIN locked ON locked.item_id = tasks.id
            WHERE id < $currentTaskId AND parent_id = $parentId
            ORDER BY id DESC LIMIT 1""".as(lp.*).headOption match {
        case Some(t) => Some(t)
        case None =>
          SQL"""SELECT * FROM tasks
                INNER JOIN locked ON locked.item_id = tasks.id
                WHERE parent_id = $parentId
                ORDER BY id DESC LIMIT 1""".as(lp.*).headOption
      }
    }
  }

  /**
    * Gets a random task based on the tags, and can include project and challenge restrictions as well.
    * The sql query that is built to execute this could be costly on larger datasets, the reason being is
    * that it will basically execute the same query twice, once to retrieve the objects that match
    * the criteria and the second time to get an accurate random offset to choose a random tasks from
    * all the matching tasks in the set. This will most likely always be called with a limit of 1.
    *
    * @param user The user executing the request
    * @param params The search parameters that will define the filters for the random selection
    * @param limit The amount of tags that should be returned
    * @return A list of random tags matching the above criteria, an empty list if none match
    */
  def getRandomTasks(user:User, params: SearchParameters,
                     limit:Int=(-1)) : List[Task] = {
    val taskTagIds = tagDAL.retrieveListByName(params.taskTags.map(_.toLowerCase)).map(_.id)
    val challengeTagIds = tagDAL.retrieveListByName(params.challengeTags.map(_.toLowerCase)).map(_.id)
    val firstQuery =
      s"""SELECT tasks.$retrieveColumns FROM tasks
          INNER JOIN challenges c ON c.id = tasks.parent_id
          INNER JOIN projects p ON p.id = c.parent_id
          LEFT JOIN locked l ON l.item_id = tasks.id
       """.stripMargin
    val secondQuery =
      """SELECT COUNT(*) FROM tasks
         INNER JOIN challenges c ON c.id = tasks.parent_id
         INNER JOIN projects p ON p.id = c.parent_id
         LEFT JOIN locked l ON l.item_id = tasks.id
      """.stripMargin

    val parameters = new ListBuffer[NamedParameter]()
    val queryBuilder = new StringBuilder
    // The default where clause will check to see if the parents are enabled, that the task is
    // not locked (or if it is, it is locked by the current user) and that the status of the task
    // is either Created or Skipped
    val whereClause = new StringBuilder(
      s"""WHERE c.enabled = TRUE AND p.enabled = TRUE AND
              (l.id IS NULL OR l.user_id = ${user.id}) AND
              tasks.status IN (${Task.STATUS_CREATED}, ${Task.STATUS_SKIPPED})""")

    params.challengeId match {
      case Some(id) =>
        whereClause ++= " AND tasks.parent_id = {parentId} "
        parameters += ('parentId -> ParameterValue.toParameterValue(params.challengeId))
      case None =>
        if (challengeTagIds.nonEmpty) {
          queryBuilder ++= "INNER JOIN tags_on_challenges tc ON tc.challenge_id = c.id "
          whereClause ++= " AND tc.tag_id IN ({challengeids})"
          parameters += ('challengeids -> ParameterValue.toParameterValue(challengeTagIds))
        }
        if (params.challengeSearch.nonEmpty) {
          whereClause ++= s" ${searchField("c.name", "AND", "challengeSearch")}"
          parameters += ('challengeSearch -> search(params.challengeSearch))
        }
    }

    params.projectId match {
      case Some(id) =>
        whereClause ++= " AND p.id = {projectId} "
        parameters += ('projectId -> ParameterValue.toParameterValue(params.projectId))
      case None =>
        if (params.projectSearch.nonEmpty) {
          whereClause ++= s" ${searchField("p.name", "AND", "projectSearch")}"
          parameters += ('projectSearch -> search(params.projectSearch))
        }
    }

    if (taskTagIds.nonEmpty) {
      queryBuilder ++= "INNER JOIN tags_on_tasks tt ON tt.task_id = tasks.id "
      whereClause ++= " AND tt.tag_id IN ({tagids}) "
      parameters += ('tagids -> ParameterValue.toParameterValue(taskTagIds))
    }
    if (params.taskSearch.nonEmpty) {
      whereClause ++= s" ${searchField("tasks.name", "AND", "taskSearch")}"
      parameters += ('taskSearch -> search(params.taskSearch))
    }


    val query = s"$firstQuery ${queryBuilder.toString} ${whereClause.toString} " +
                s"OFFSET FLOOR(RANDOM()*(" +
                s"$secondQuery ${queryBuilder.toString} ${whereClause.toString}" +
                s")) LIMIT ${sqlLimit(limit)}"

    implicit val ids = List[Long]()
    cacheManager.withIDListCaching { implicit cachedItems =>
      db.withTransaction { implicit c =>
        // if a user is requesting a task, then we can unlock all other tasks for that user, as only a single
        // task can be locked at a time
        unlockAllItems(user, Some(TaskType()))
        val tasks = SQLWithParameters(query, parameters).as(parser.*)
        // once we have the tasks, we need to lock each one, if any fail to lock we just remove
        // them from the list. A guest user will not lock any tasks, but when logged in will be
        // required to refetch the current task, and if it is locked, then will have to get another
        // task
        if (!user.guest) {
          val taskList = tasks.filter(lockItem(user, _) > 0)
          if (taskList.isEmpty) {
            throw new NotFoundException("No tasks found.")
          }
          taskList
        } else {
          tasks
        }
      }
    }
  }
}
