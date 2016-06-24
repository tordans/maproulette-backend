package org.maproulette.models.dal

import java.sql.Connection
import javax.inject.{Inject, Provider, Singleton}

import anorm._
import anorm.SqlParser._
import org.apache.commons.lang3.StringUtils
import org.maproulette.Config
import org.maproulette.actions.{ActionManager, TaskType}
import org.maproulette.cache.CacheManager
import org.maproulette.models.{Challenge, Lock, Project, Task}
import org.maproulette.exception.{InvalidException, NotFoundException, UniqueViolationException}
import org.maproulette.models.utils.DALHelper
import org.maproulette.permissions.Permission
import org.maproulette.session.{SearchParameters, User}
import play.api.Logger
import play.api.db.Database
import play.api.libs.json._

import scala.collection.mutable.ListBuffer
import scala.util.{Success, Failure, Try}

/**
  * The data access layer for the Task objects
  *
  * @author cuthbertm
  */
@Singleton
class TaskDAL @Inject() (override val db:Database,
                         override val tagDAL: TagDAL, config:Config,
                         override val permission:Permission,
                         projectDAL: Provider[ProjectDAL],
                         challengeDAL: Provider[ChallengeDAL],
                         actions:ActionManager)
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
      get[Option[String]]("tasks.instruction") ~
      get[Option[String]]("location") ~
      get[Option[Int]]("tasks.status") ~
      get[Int]("tasks.priority") map {
      case id ~ name ~ parent_id ~ instruction ~ location ~ status ~ priority =>
        Task(id, name, parent_id, instruction, location, getTaskGeometries(id), status, priority)
    }
  }

  /**
    * This will retrieve the root object in the hierarchy of the object, by default the root
    * object is itself.
    *
    * @param obj Either a id for the challenge, or the challenge itself
    * @param c  The connection if any
    * @return The object that it is retrieving
    */
  override def retrieveRootObject(obj:Either[Long, Task], user:User)(implicit c: Connection): Option[Project] = {
    obj match {
      case Left(id) =>
        permission.hasReadAccess(TaskType(), user)(id)
        projectDAL.get().cacheManager.withOptionCaching { () =>
          withMRConnection { implicit c =>
            SQL"""SELECT p.* FROM projects p
             INNER JOIN challenges c ON c.parent_id = p.id
             INNER JOIN task t ON t.parent_id = c.id
             WHERE t.id = $id
           """.as(projectDAL.get().parser.*).headOption
          }
        }
      case Right(task) =>
        permission.hasReadAccess(task, user)
        projectDAL.get().cacheManager.withOptionCaching { () =>
          withMRConnection { implicit c =>
            SQL"""SELECT p.* FROM projects p
             INNER JOIN challenges c ON c.parent_id = p.id
             WHERE c.id = ${task.parent}
           """.as(projectDAL.get().parser.*).headOption
          }
        }
    }
  }

  /**
    * Retrieve all the geometries for the task
    *
    * @param id Id for the task
    * @return A feature collection geojson of all the task geometries
    */
  private def getTaskGeometries(id:Long)(implicit c:Connection=null) : String = {
    withMRConnection { implicit c =>
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
  override def insert(task:Task, user:User)(implicit c:Connection=null) : Task = {
    permission.hasWriteAccess(task, user)
    cacheManager.withOptionCaching { () =>
      withMRTransaction { implicit c =>
        var parameters = Seq(
          NamedParameter("name", ParameterValue.toParameterValue(task.name)),
          NamedParameter("parent", ParameterValue.toParameterValue(task.parent)),
          NamedParameter("instruction", ParameterValue.toParameterValue(task.instruction)),
          NamedParameter("priority", ParameterValue.toParameterValue(task.priority))
        )
        val locationValue = if (task.location.isEmpty || StringUtils.isEmpty(task.location.get)) {
          ("", "")
        } else {
          parameters = parameters :+ NamedParameter("location", ParameterValue.toParameterValue(task.location.get.toString))
          ("location,", s"ST_SetSRID(ST_GeomFromGeoJSON({location}),4326),")
        }
        // status is ignored on insert and always set to CREATED
        val query = s"""INSERT INTO tasks (name, parent_id, ${locationValue._1} instruction, status, priority)
                      VALUES ({name}, {parent}, ${locationValue._2} {instruction}, ${Task.STATUS_CREATED}, {priority}
                      ) ON CONFLICT(parent_id, LOWER(name)) DO NOTHING RETURNING id"""
        SQL(query).on(parameters: _*).as(long("id").*).headOption match {
          case Some(id) =>
            updateGeometries(id, Json.parse(task.geometries), false, true)
            Some(task.copy(id = id))
          case None => None
        }
      }
    } match {
      case Some(t) => t
      case None => throw new UniqueViolationException(s"Task with name ${task.name} already exists in the database")
    }
  }

  /**
    * Updates a task object in the database.
    *
    * @param value A json object containing fields to be updated for the task
    * @param user The user executing the task
    * @param id The id of the object that you are updating
    * @return An optional object, it will return None if no object found with a matching id that was supplied
    */
  override def update(value:JsValue, user:User)(implicit id:Long, c:Connection=null): Option[Task] = {
    cacheManager.withUpdatingCache(Long => retrieveById) { implicit cachedItem =>
      permission.hasWriteAccess(cachedItem, user)
      withMRTransaction { implicit c =>
        val name = (value \ "name").asOpt[String].getOrElse(cachedItem.name)
        val parentId = (value \ "parentId").asOpt[Long].getOrElse(cachedItem.parent)
        val instruction = (value \ "instruction").asOpt[String].getOrElse(cachedItem.instruction.getOrElse(""))
        val location = (value \ "location").asOpt[String].getOrElse(cachedItem.location.getOrElse(""))
        val status = (value \ "status").asOpt[Int].getOrElse(cachedItem.status.getOrElse(0))
        if (!Task.isValidStatusProgression(cachedItem.status.getOrElse(0), status)) {
          throw new InvalidException(s"Could not set status for task [$id], " +
            s"progression from ${cachedItem.status.getOrElse(0)} to $status not valid.")
        }
        val priority = (value \ "priority").asOpt[Int].getOrElse(cachedItem.priority)

        var parameters = Seq(
          NamedParameter("name", ParameterValue.toParameterValue(name)),
          NamedParameter("parentId", ParameterValue.toParameterValue(parentId)),
          NamedParameter("instruction", ParameterValue.toParameterValue(instruction)),
          NamedParameter("status", ParameterValue.toParameterValue(status)),
          NamedParameter("id", ParameterValue.toParameterValue(id)),
          NamedParameter("priority", ParameterValue.toParameterValue(priority))
        )
        val locationInfo = if (StringUtils.isEmpty(location)) {
          ""
        } else {
          parameters = parameters :+ NamedParameter("location", location)
          "location = ST_SetSRID(ST_GeomFromGeoJSON({location}),4326),"
        }

        val query = s"""UPDATE tasks SET name = {name}, parent_id = {parentId},
                        instruction = {instruction}, $locationInfo status = {status},
                        priority = {priority}
                      WHERE id = {id}"""

        SQL(query).on(parameters: _*).executeUpdate()
        val geometries = (value \ "geometries").asOpt[String] match {
          case Some(geom) =>
            updateGeometries(id, Json.parse(geom))
            geom
          case None => cachedItem.geometries
        }
        Some(Task(id, name, parentId, Some(instruction), Some(location), geometries, Some(status), priority))
      }
    }
  }

  /**
    * This is a merge update function that will update the function if it exists otherwise it will
    * insert a new item.
    *
    * @param element The element that needs to be inserted or updated. Although it could be updated,
    *                it requires the element itself in case it needs to be inserted
    * @param user    The user that is executing the function
    * @param id      The id of the element that is being updated/inserted
    * @param c       A connection to execute against
    * @return
    */
  override def mergeUpdate(element: Task, user: User)(implicit id: Long, c: Connection): Option[Task] = {
    permission.hasWriteAccess(element, user)
    cacheManager.withOptionCaching { () =>
      withMRTransaction { implicit c =>
        val query = "SELECT create_update_task({name}, {parentId}, {instruction}, {status}, {id}, {priority}, {reset})"

        val updatedTaskId = SQL(query).on(
          NamedParameter("name", ParameterValue.toParameterValue(element.name)),
          NamedParameter("parentId", ParameterValue.toParameterValue(element.parent)),
          NamedParameter("instruction", ParameterValue.toParameterValue(element.instruction)),
          NamedParameter("status", ParameterValue.toParameterValue(element.status.getOrElse(Task.STATUS_CREATED))),
          NamedParameter("id", ParameterValue.toParameterValue(element.id)),
          NamedParameter("priority", ParameterValue.toParameterValue(element.priority)),
          NamedParameter("reset", ParameterValue.toParameterValue(config.taskReset + " days"))
        ).as(long("create_update_task").*).head
        if (StringUtils.isNotEmpty(element.geometries)) {
          updateGeometries(updatedTaskId, Json.parse(element.geometries))
        }
        Some(element.copy(id = updatedTaskId))
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
  private def updateGeometries(taskId:Long, value:JsValue, setLocation:Boolean=false, isNew:Boolean=false)(implicit c:Connection=null) : Unit = {
    withMRTransaction { implicit c =>
      if (!isNew) {
        SQL"""DELETE FROM task_geometries WHERE task_id = $taskId""".executeUpdate()
      }
      val features = (value \ "features").as[List[JsValue]]
      val indexedValues = features.zipWithIndex
      val rows = indexedValues.map {
        case (_, i) => s"({taskId}, ST_SetSRID(ST_GeomFromGeoJSON({geom_$i}),4326), {props_$i}::hstore)"
      }.mkString(",")
      val parameters = indexedValues.flatMap { case (featureJson, i) =>
        val geometry = ()
        val props = (featureJson \ "properties").asOpt[JsValue] match {
          case Some(p) => p.as[Map[String, String]].map(v => s""""${v._1}"=>"${v._2.replaceAll("\\\"", "\\\\\"")}"""").mkString(",")
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
      c.commit()
    }
    updateTaskLocation(taskId)
  }

  def updateTaskLocation(taskId:Long) = {
    db.withTransaction { implicit c =>
      // Update the location of the particular task
      SQL"""UPDATE tasks
            SET location = (SELECT ST_Centroid(ST_Collect(ST_Makevalid(geom)))
          					        FROM task_geometries WHERE task_id = $taskId)
            WHERE id = $taskId
        """.executeUpdate()
    }
  }

  def updateTaskLocations(challengeId:Long) = {
    db.withTransaction { implicit c =>
      // update all the tasks of a particular challenge
      SQL"""DO $$
            DECLARE
            	rec RECORD;
            BEGIN
            	FOR rec IN SELECT task_id, ST_Centroid(ST_Collect(ST_Makevalid(geom))) AS location
                          FROM task_geometries tg
                          INNER JOIN tasks t on t.id = tg.task_id
                          WHERE t.parent_id = $challengeId
                          GROUP BY task_id LOOP
            		UPDATE tasks SET location = rec.location WHERE tasks.id = rec.task_id and parent_id = $challengeId;
            	END LOOP;
            END$$;
        """.executeUpdate()
    }
  }

  /**
    * This function will update the tasks priority based on the parent challenge information. It will
    * check first to see if it falls inside the HIGH priority bucket, then MEDIUM then LOW. If it doesn't
    * fall into any priority bucket, it will then set the priority to the default priority defined
    * in the parent challenge
    *
    * @param taskId The id for the task to update the priority for
    * @param c The database connection
    */
  def updateTaskPriority(taskId:Long, user:User)(implicit c:Connection=null) : Unit = {
    withMRTransaction{ implicit c =>
      implicit val id = taskId
      cacheManager.withUpdatingCache(Long => retrieveById) { implicit task =>
        permission.hasWriteAccess(task, user)
        // get the parent challenge, as we need the priority information
        val parentChallenge = challengeDAL.get().retrieveById(task.parent) match {
          case Some(c) => c
          case None => throw new NotFoundException(s"No parent was found for task [$taskId], this should never happen.")
        }
        val newPriority = getTaskPriority(task, parentChallenge)
        update(Json.obj("priority" -> newPriority), user)
      }
    }
  }

  /**
    * Gets the task priority
    *
    * @param task The task
    * @param parent The parent Challenge
    * @return Priority HIGH = 0, MEDIUM = 1, LOW = 2
    */
  def getTaskPriority(task:Task, parent:Challenge) : Int = {
    val matchingList = task.getGeometryProperties().flatMap {
      props => if (parent.isHighPriority(props)) {
        Some(Challenge.PRIORITY_HIGH)
      } else if (parent.isMediumPriority(props)) {
        Some(Challenge.PRIORITY_MEDIUM)
      } else if (parent.isLowRulePriority(props)) {
        Some(Challenge.PRIORITY_LOW)
      } else {
        None
      }
    }
    if (matchingList.isEmpty) {
      parent.defaultPriority
    } else if (matchingList.contains(Challenge.PRIORITY_HIGH)) {
      Challenge.PRIORITY_HIGH
    } else if (matchingList.contains(Challenge.PRIORITY_MEDIUM)) {
      Challenge.PRIORITY_MEDIUM
    } else {
      Challenge.PRIORITY_LOW
    }
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
  def setTaskStatus(task:Task, status:Int, user:User)(implicit c:Connection=null) : Int = {
    if (!Task.isValidStatusProgression(task.status.getOrElse(Task.STATUS_CREATED), status)) {
      throw new InvalidException("Invalid task status supplied.")
    } else if (user.guest) {
      throw new IllegalAccessException("Guest users cannot make edits to tasks.")
    }

    val updatedRows = withMRTransaction { implicit c =>
      val updatedRows = SQL"""UPDATE tasks t SET status = $status
          FROM tasks t2
          LEFT JOIN locked l ON l.item_id = t2.id AND l.item_type = ${task.itemType.typeId}
          WHERE t.id = ${task.id} AND (l.user_id = ${user.id} OR l.user_id IS NULL)""".executeUpdate()
      // if returning 0, then this is because the item is locked by a different user
      if (updatedRows == 0) {
        throw new IllegalAccessException(s"Current task [${task.id} is locked by another user, cannot update status at this time.")
      }
      actions.setStatusAction(user, task, status)

      // if you set the status successfully on a task you will lose the lock of that task
      try {
        unlockItem(user, task)
      } catch {
        case e:Exception => Logger.warn(e.getMessage)
      }
      updatedRows
    }

    cacheManager.withOptionCaching { () => Some(task.copy(status = Some(status))) }
    updatedRows
  }

  /**
    * Simple query to retrieve the next task in the sequence
    *
    * @param parentId The parent of the task
    * @param currentTaskId The current task that we are basing our query from
    * @return An optional task, if no more tasks in the list will retrieve the first task
    */
  def getNextTaskInSequence(parentId:Long, currentTaskId:Long)(implicit c:Connection=null) : Option[(Task, Lock)] = {
    withMRConnection { implicit c =>
      val lp = for {
        task <- parser
        lock <- lockedParser
      } yield task -> lock
      val query = s"""SELECT locked.*, tasks.$retrieveColumns FROM tasks
                      LEFT JOIN locked ON locked.item_id = tasks.id
                      WHERE tasks.id > $currentTaskId AND tasks.parent_id = $parentId
                      ORDER BY tasks.id ASC LIMIT 1"""
      SQL(query).as(lp.*).headOption match {
        case Some(t) => Some(t)
        case None =>
          val loopQuery = s"""SELECT locked.*, tasks.$retrieveColumns FROM tasks
                              LEFT JOIN locked ON locked.item_id = tasks.id
                              WHERE tasks.parent_id = $parentId
                              ORDER BY tasks.id ASC LIMIT 1"""
          SQL(loopQuery).as(lp.*).headOption
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
  def getPreviousTaskInSequence(parentId:Long, currentTaskId:Long)(implicit c:Connection=null) : Option[(Task, Lock)] = {
    withMRConnection { implicit c =>
      val lp = for {
        task <- parser
        lock <- lockedParser
      } yield task -> lock
      val query = s"""SELECT locked.*, tasks.$retrieveColumns FROM tasks
                      LEFT JOIN locked ON locked.item_id = tasks.id
                      WHERE tasks.id < $currentTaskId AND tasks.parent_id = $parentId
                      ORDER BY tasks.id DESC LIMIT 1"""
      SQL(query).as(lp.*).headOption match {
        case Some(t) => Some(t)
        case None =>
          val loopQuery = s"""SELECT locked.*, tasks.$retrieveColumns FROM tasks
                              LEFT JOIN locked ON locked.item_id = tasks.id
                              WHERE tasks.parent_id = $parentId
                              ORDER BY tasks.id DESC LIMIT 1"""
          SQL(loopQuery).as(lp.*).headOption
      }
    }
  }

  def getRandomTasksWithPriority(user:User, params:SearchParameters, limit:Int=(-1))
                                (implicit c:Connection=null) : List[Task] = {
    val highPriorityTasks = Try(getRandomTasks(user, params, limit)) match {
      case Success(res) => res
      case Failure(f) => List.empty
    }
    if (highPriorityTasks.isEmpty) {
      val mediumPriorityTasks = Try(getRandomTasks(user, params, limit, Challenge.PRIORITY_MEDIUM)) match {
        case Success(res) => res
        case Failure(f) => List.empty
      }
      if (mediumPriorityTasks.isEmpty) {
        getRandomTasks(user, params, limit, Challenge.PRIORITY_LOW)
      } else {
        mediumPriorityTasks
      }
    } else {
      highPriorityTasks
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
  def getRandomTasks(user:User, params: SearchParameters, limit:Int=(-1), priority:Int=Challenge.PRIORITY_HIGH)
                    (implicit c:Connection=null) : List[Task] = {
    val enabledClause = if (params.projectEnabled || (params.projectEnabled && params.challengeEnabled)) {
      "c.enabled = TRUE AND p.enabled = TRUE AND"
    } else {
      ""
    }
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
      s"""WHERE $enabledClause tasks.priority = $priority AND
              (l.id IS NULL OR l.user_id = ${user.id}) AND
              tasks.status IN (${Task.STATUS_CREATED}, ${Task.STATUS_SKIPPED}, ${Task.STATUS_TOO_HARD})""")

    params.getChallengeId match {
      case Some(id) =>
        whereClause ++= " AND tasks.parent_id = {parentId} "
        parameters += ('parentId -> ParameterValue.toParameterValue(id))
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

    params.getProjectId match {
      case Some(id) =>
        whereClause ++= " AND p.id = {projectId} "
        parameters += ('projectId -> ParameterValue.toParameterValue(id))
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
      withMRTransaction { implicit c =>
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
