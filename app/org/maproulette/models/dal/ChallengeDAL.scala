// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.models.dal

import java.sql.Connection
import javax.inject.{Inject, Provider, Singleton}

import anorm._
import anorm.SqlParser._
import org.apache.commons.lang3.StringUtils
import org.maproulette.Config
import org.maproulette.actions.{Actions, ChallengeType, TaskType}
import org.maproulette.cache.CacheManager
import org.maproulette.exception.{NotFoundException, UniqueViolationException}
import org.maproulette.models._
import org.maproulette.permissions.Permission
import org.maproulette.session.User
import play.api.db.Database
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.Future

/**
  * The challenge data access layer handles all calls for challenges going to the database. Most
  * worked is delegated to the ParentDAL and BaseDAL, but a couple of specific function like
  * insert and update found here.
  *
  * @author cuthbertm
  */
@Singleton
class ChallengeDAL @Inject() (override val db:Database, taskDAL: TaskDAL,
                              override val tagDAL: TagDAL,
                              projectDAL: Provider[ProjectDAL],
                              override val permission:Permission)
  extends ParentDAL[Long, Challenge, Task] with TagDALMixin[Challenge] {

  import scala.concurrent.ExecutionContext.Implicits.global
  // The manager for the challenge cache
  override val cacheManager = new CacheManager[Long, Challenge]
  // The name of the challenge table
  override val tableName: String = "challenges"
  // The name of the table for it's children Tasks
  override val childTable: String = "tasks"
  // The row parser for it's children defined in the TaskDAL
  override val childParser = taskDAL.parser
  override val childColumns: String = taskDAL.retrieveColumns

  /**
    * The row parser for Anorm to enable the object to be read from the retrieved row directly
    * to the Challenge object.
    */
  override val parser: RowParser[Challenge] = {
    get[Long]("challenges.id") ~
      get[String]("challenges.name") ~
      get[Option[String]]("challenges.description") ~
      get[Long]("challenges.parent_id") ~
      get[String]("challenges.instruction") ~
      get[Int]("challenges.difficulty") ~
      get[Option[String]]("challenges.blurb") ~
      get[Boolean]("challenges.enabled") ~
      get[Int]("challenges.challenge_type") ~
      get[Boolean]("challenges.featured") ~
      get[Option[String]]("challenges.overpass_ql") ~
      get[Option[String]]("challenges.remote_geo_json") ~
      get[Option[Int]]("challenges.status") ~
      get[Int]("challenges.default_priority") ~
      get[Option[String]]("challenges.high_priority_rule") ~
      get[Option[String]]("challenges.medium_priority_rule") ~
      get[Option[String]]("challenges.low_priority_rule") ~
      get[Int]("challenges.default_zoom") ~
      get[Int]("challenges.min_zoom") ~
      get[Int]("challenges.max_zoom") ~
      get[Option[Int]]("challenges.default_basemap") ~
      get[Option[String]]("challenges.custom_basemap") map {
      case id ~ name ~ description ~ parentId ~ instruction ~ difficulty ~ blurb ~
        enabled ~ challenge_type ~ featured ~ overpassql ~ remoteGeoJson ~ status ~
        defaultPriority ~ highPriorityRule ~ mediumPriorityRule ~ lowPriorityRule ~
        defaultZoom ~ minZoom ~ maxZoom ~ defaultBasemap ~ customBasemap =>
        val hpr = highPriorityRule match {
          case Some(c) if StringUtils.isEmpty(c) || StringUtils.equals(c, "{}") => None
          case r => r
        }
        val mpr = mediumPriorityRule match {
          case Some(c) if StringUtils.isEmpty(c) || StringUtils.equals(c, "{}") => None
          case r => r
        }
        val lpr = lowPriorityRule match {
          case Some(c) if StringUtils.isEmpty(c) || StringUtils.equals(c, "{}") => None
          case r => r
        }
        new Challenge(id, name, description, parentId, instruction, difficulty, blurb,
          enabled, challenge_type, featured, overpassql, remoteGeoJson, status,
          defaultPriority, hpr, mpr, lpr, defaultZoom, minZoom, maxZoom, defaultBasemap, customBasemap)
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
  override def retrieveRootObject(obj:Either[Long, Challenge], user:User)(implicit c:Option[Connection]=None): Option[Project] = {
    obj match {
      case Left(id) =>
        this.permission.hasReadAccess(ChallengeType(), user)(id)
        this.projectDAL.get().cacheManager.withOptionCaching { () =>
          this.withMRConnection { implicit c =>
            SQL"""SELECT p.* FROM projects p
             INNER JOIN challenges c ON c.parent_id = p.id
             WHERE c.id = $id
           """.as(projectDAL.get().parser.*).headOption
          }
        }
      case Right(challenge) =>
        this.permission.hasReadAccess(challenge, user)
        this.projectDAL.get().cacheManager.withOptionCaching { () =>
          this.withMRConnection { implicit c =>
            SQL"""SELECT * FROM projects WHERE id = ${challenge.parent}""".as(projectDAL.get().parser.*).headOption
          }
        }
    }
  }

  /**
    * Inserts a new Challenge object into the database. It will also place it in the cache after
    * inserting the object.
    *
    * @param challenge The challenge to insert into the database
    * @return The object that was inserted into the database. This will include the newly created id
    */
  override def insert(challenge: Challenge, user:User)(implicit c:Option[Connection]=None): Challenge = {
    this.permission.hasWriteAccess(challenge, user)
    this.cacheManager.withOptionCaching { () =>
      this.withMRTransaction { implicit c =>
        SQL"""INSERT INTO challenges (name, parent_id, difficulty, description, blurb,
                                      instruction, enabled, challenge_type, featured, overpass_ql,
                                      remote_geo_json, status, default_priority, high_priority_rule,
                                      medium_priority_rule, low_priority_rule, default_zoom, min_zoom,
                                      max_zoom, default_basemap, custom_basemap)
              VALUES (${challenge.name}, ${challenge.parent}, ${challenge.difficulty},
                      ${challenge.description}, ${challenge.blurb}, ${challenge.instruction},
                      ${challenge.enabled}, ${challenge.challengeType}, ${challenge.featured},
                      ${challenge.overpassQL}, ${challenge.remoteGeoJson}, ${challenge.status},
                      ${challenge.defaultPriority}, ${challenge.highPriorityRule}, ${challenge.mediumPriorityRule},
                      ${challenge.lowPriorityRule}, ${challenge.defaultZoom}, ${challenge.minZoom},
                      ${challenge.maxZoom}, ${challenge.defaultBasemap}, ${challenge.customBasemap}
                      ) ON CONFLICT(parent_id, LOWER(name)) DO NOTHING RETURNING *""".as(this.parser.*).headOption
      }
    } match {
      case Some(value) => value
      case None => throw new UniqueViolationException(s"Challenge with name ${challenge.name} already exists in the database")
    }
  }

  /**
    * Updates a challenge. Uses the updatingCache so will first retrieve the object and make sure
    * to update only values supplied by the json. After updated will update the cache as well
    *
    * @param updates The updates in json format
    * @param id The id of the object that you are updating
    * @return An optional object, it will return None if no object found with a matching id that was supplied
    */
  override def update(updates:JsValue, user:User)(implicit id:Long, c:Option[Connection]=None): Option[Challenge] = {
    var updatedPriorityRules = false
    val updatedChallenge = this.cacheManager.withUpdatingCache(Long => retrieveById) { implicit cachedItem =>
      this.permission.hasWriteAccess(cachedItem, user)
      val highPriorityRule = (updates \ "highPriorityRule").asOpt[String].getOrElse(cachedItem.highPriorityRule.getOrElse("")) match {
        case x if Challenge.isValidRule(Some(x)) => x
        case _ => ""
      }
      val mediumPriorityRule = (updates \ "mediumPriorityRule").asOpt[String].getOrElse(cachedItem.mediumPriorityRule.getOrElse("")) match {
        case x if Challenge.isValidRule(Some(x)) => x
        case _ => ""
      }
      val lowPriorityRule = (updates \ "lowPriorityRule").asOpt[String].getOrElse(cachedItem.lowPriorityRule.getOrElse("")) match {
        case x if Challenge.isValidRule(Some(x)) => x
        case _ => ""
      }
      this.withMRTransaction { implicit c =>
        val name = (updates \ "name").asOpt[String].getOrElse(cachedItem.name)
        val parentId = (updates \ "parentId").asOpt[Long].getOrElse(cachedItem.parent)
        val difficulty = (updates \ "difficulty").asOpt[Int].getOrElse(cachedItem.difficulty)
        val description =(updates \ "description").asOpt[String].getOrElse(cachedItem.description.getOrElse(""))
        val blurb = (updates \ "blurb").asOpt[String].getOrElse(cachedItem.blurb.getOrElse(""))
        val instruction = (updates \ "instruction").asOpt[String].getOrElse(cachedItem.instruction)
        val enabled = (updates \ "enabled").asOpt[Boolean].getOrElse(cachedItem.enabled)
        val featured = (updates \ "featured").asOpt[Boolean].getOrElse(cachedItem.featured)
        val overpassQL = (updates \ "overpassQL").asOpt[String].getOrElse(cachedItem.overpassQL.getOrElse(""))
        val remoteGeoJson = (updates \ "remoteGeoJson").asOpt[String].getOrElse(cachedItem.remoteGeoJson.getOrElse(""))
        val overpassStatus = (updates \ "status").asOpt[Int].getOrElse(cachedItem.status.getOrElse(Challenge.STATUS_NA))
        val defaultPriority = (updates \ "defaultPriority").asOpt[Int].getOrElse(cachedItem.defaultPriority)
        // if any of the priority rules have changed then we need to run the update priorities task
        if (!StringUtils.equalsIgnoreCase(highPriorityRule, cachedItem.highPriorityRule.getOrElse("")) ||
            !StringUtils.equalsIgnoreCase(mediumPriorityRule, cachedItem.mediumPriorityRule.getOrElse("")) ||
            !StringUtils.equalsIgnoreCase(lowPriorityRule, cachedItem.lowPriorityRule.getOrElse(""))) {
          updatedPriorityRules = true
        }
        val defaultZoom = (updates \ "defaultZoom").asOpt[Int].getOrElse(cachedItem.defaultZoom)
        val minZoom = (updates \ "minZoom").asOpt[Int].getOrElse(cachedItem.minZoom)
        val maxZoom = (updates \ "maxZoom").asOpt[Int].getOrElse(cachedItem.maxZoom)
        val defaultBasemap = (updates \ "defaultBasemap").asOpt[Int].getOrElse(cachedItem.defaultBasemap.getOrElse(-1))
        val customBasemap = (updates \ "customBasemap").asOpt[String].getOrElse(cachedItem.customBasemap.getOrElse(""))

        SQL"""UPDATE challenges SET name = $name, parent_id = $parentId, difficulty = $difficulty,
                description = $description, blurb = $blurb, instruction = $instruction,
                enabled = $enabled, featured = $featured, overpass_ql = $overpassQL,
                remote_geo_json = $remoteGeoJson, status = $overpassStatus, default_priority = $defaultPriority,
                high_priority_rule = ${if (StringUtils.isEmpty(highPriorityRule)) { Option.empty[String] } else { Some(highPriorityRule) }},
                medium_priority_rule = ${if (StringUtils.isEmpty(mediumPriorityRule)) { Option.empty[String] } else { Some(mediumPriorityRule) }},
                low_priority_rule = ${if (StringUtils.isEmpty(lowPriorityRule)) { Option.empty[String] } else { Some(lowPriorityRule) }},
                default_zoom = $defaultZoom, min_zoom = $minZoom, max_zoom = $maxZoom, default_basemap = $defaultBasemap,
                custom_basemap = $customBasemap
              WHERE id = $id RETURNING *""".as(parser.*).headOption
      }
    }
    // update the task priorities in the background
    if (updatedPriorityRules) {
      Future { updateTaskPriorities(user) }
    }
    updatedChallenge
  }

  override def find(searchString:String, limit:Int = Config.DEFAULT_LIST_SIZE, offset:Int = 0, onlyEnabled:Boolean=false,
            orderColumn:String="id", orderDirection:String="ASC")
           (implicit parentId:Long = -1, c:Option[Connection]=None) : List[Challenge] =
    findByType(searchString, limit, offset, onlyEnabled, orderColumn, orderDirection)

  def findByType(searchString:String, limit:Int = Config.DEFAULT_LIST_SIZE, offset:Int = 0, onlyEnabled:Boolean=false,
            orderColumn:String="id", orderDirection:String="ASC", challengeType:Int=Actions.ITEM_TYPE_CHALLENGE)
           (implicit parentId:Long= -1, c:Option[Connection]=None) : List[Challenge] = {
    this.withMRConnection { implicit c =>
      val query = s"""SELECT ${this.retrieveColumns} FROM challenges c
                      INNER JOIN projects p ON p.id = c.parent_id
                      WHERE challenge_type = $challengeType
                      ${this.searchField("c.name")}
                      ${this.enabled(onlyEnabled, "p")} ${this.enabled(onlyEnabled, "c")}
                      ${this.parentFilter(parentId)}
                      ${this.order(Some(orderColumn), orderDirection, "c")}
                      LIMIT ${this.sqlLimit(limit)} OFFSET {offset}"""
      SQL(query).on('ss -> searchString, 'offset -> offset).as(this.parser.*)
    }
  }

  override def list(limit:Int = Config.DEFAULT_LIST_SIZE, offset:Int = 0, onlyEnabled:Boolean=false, searchString:String="",
                    orderColumn:String="id", orderDirection:String="ASC")
                   (implicit parentId:Long= -1, c:Option[Connection]=None) : List[Challenge] =
    this.listByType(limit, offset, onlyEnabled, searchString, orderColumn, orderDirection)

  /**
    * This is a dangerous function as it will return all the objects available, so it could take up
    * a lot of memory
    */
  def listByType(limit:Int = Config.DEFAULT_LIST_SIZE, offset:Int = 0, onlyEnabled:Boolean=false, searchString:String="",
           orderColumn:String="id", orderDirection:String="ASC", challengeType:Int=Actions.ITEM_TYPE_CHALLENGE)
          (implicit parentId:Long = -1, c:Option[Connection]=None) : List[Challenge] = {
    implicit val ids = List.empty
    this.cacheManager.withIDListCaching { implicit uncachedIDs =>
      this.withMRConnection { implicit c =>
        val query = s"""SELECT $retrieveColumns FROM challenges c
                        INNER JOIN projects p ON p.id = c.parent_id
                        WHERE challenge_type = $challengeType
                        ${this.searchField("c.name")}
                        ${this.enabled(onlyEnabled, "p")} ${this.enabled(onlyEnabled, "c")}
                        ${this.parentFilter(parentId)}
                        ${this.order(Some(orderColumn), orderDirection, "c")}
                        LIMIT ${this.sqlLimit(limit)} OFFSET {offset}"""
        SQL(query).on('ss -> this.search(searchString),
          'offset -> ParameterValue.toParameterValue(offset)
        ).as(this.parser.*)
      }
    }
  }

  /**
    * Gets the featured challenges
    *
    * @param limit The number of challenges to retrieve
    * @param offset For paging, ie. the page number starting at 0
    * @param enabledOnly if true will only return enabled challenges
    * @return list of challenges
    */
  def getFeaturedChallenges(limit:Int, offset:Int, enabledOnly:Boolean=true)(implicit c:Option[Connection]=None) : List[Challenge] = {
    this.withMRConnection { implicit c =>
      val query = s"""SELECT * FROM challenges c
                      INNER JOIN projects p ON p.id = c.parent_id
                      WHERE featured = TRUE ${this.enabled(enabledOnly, "c")} ${this.enabled(enabledOnly, "p")}
                      AND 0 < (SELECT COUNT(*) FROM tasks WHERE parent_id = c.id)
                      LIMIT ${this.sqlLimit(limit)} OFFSET $offset"""
      SQL(query).as(this.parser.*)
    }
  }

  /**
    * Get the Hot challenges, these are challenges that have the most activity
    *
    * @param limit the number of challenges to retrieve
    * @param offset For paging, ie. the page number starting at 0
    * @param enabledOnly if true will only return enabled challenges
    * @return List of challenges
    */
  def getHotChallenges(limit:Int, offset:Int, enabledOnly:Boolean=true)(implicit c:Option[Connection]=None) : List[Challenge] = {
    List.empty
  }

  /**
    * Gets the new challenges
    *
    * @param limit The number of challenges to retrieve
    * @param offset For paging ie. the page number starting at 0
    * @param enabledOnly if true will only return enabled challenges
    * @return list of challenges
    */
  def getNewChallenges(limit:Int, offset:Int, enabledOnly:Boolean=true)(implicit c:Option[Connection]=None) : List[Challenge] = {
    this.withMRConnection { implicit c =>
      val query = s"""SELECT * FROM challenges c
                      INNER JOIN projects p ON c.parent_id = p.id
                      WHERE ${this.enabled(enabledOnly, "c", "")} ${this.enabled(enabledOnly, "p")}
                      ${this.order(Some("created"), "DESC", "c")}
                      LIMIT ${this.sqlLimit(limit)} OFFSET $offset"""
      SQL(query).as(this.parser.*)
    }
  }

  /**
    * Gets the combined geometry of all the tasks that are associated with the challenge
    *
    * @param challengeId The id for the challenge
    * @param statusFilter To view the geojson for only challenges with a specific status
    * @param c The implicit connection for the function
    * @return
    */
  def getChallengeGeometry(challengeId:Long, statusFilter:Option[List[Int]]=None)(implicit c:Option[Connection]=None) : String = {
    this.withMRConnection { implicit c =>
      val filter = statusFilter match {
        case Some(s) => s"AND status IN (${s.mkString(",")}"
        case None => ""
      }
      SQL"""SELECT row_to_json(fc)::text as geometries
            FROM ( SELECT 'FeatureCollection' As type, array_to_json(array_agg(f)) As features
                   FROM ( SELECT 'Feature' As type,
                                  ST_AsGeoJSON(lg.geom)::json As geometry,
                                  hstore_to_json(lg.properties) As properties
                          FROM task_geometries As lg
                          WHERE task_id IN
                          (SELECT DISTINCT id FROM tasks WHERE parent_id = $challengeId) #$filter
                    ) As f
            )  As fc""".as(str("geometries").single)
    }
  }

  /**
    * Retrieves the json that contains the central points for all the tasks
    *
    * @param challengeId The id of the challenge
    * @param statusFilter Filter the displayed task cluster points by their status
    * @return A list of clustered point objects
    */
  def getClusteredPoints(challengeId:Long, statusFilter:Option[List[Int]]=None)
                               (implicit c:Option[Connection]=None) : List[ClusteredPoint] = {
    this.withMRConnection { implicit c =>
      val filter = statusFilter match {
        case Some(s) => s"AND status IN (${s.mkString(",")}"
        case None => ""
      }
      val pointParser = long("id") ~ str("name") ~ str("instruction") ~ str("location") ~ int("status") map {
        case id ~ name ~ instruction ~ location ~ status =>
          val locationJSON = Json.parse(location)
          val coordinates = (locationJSON \ "coordinates").as[List[Double]]
          val point = Point(coordinates(1), coordinates.head)
          ClusteredPoint(id, "", point, instruction, -1, Actions.ITEM_TYPE_TASK, status)
      }
        SQL"""SELECT id, name, instruction, status,
                      ST_AsGeoJSON(location) AS location
              FROM tasks WHERE parent_id = $challengeId #$filter"""
          .as(pointParser.*)
    }
  }

  /**
    * The summary for a challenge is the status with the number of tasks associated with each status
    * underneath the given challenge
    *
    * @param id The id for the challenge
    * @return Map of status codes mapped to task counts
    */
  def getSummary(id:Long)(implicit c:Option[Connection]=None) : Map[Int, Int] = {
    this.withMRConnection { implicit c =>
      val summaryParser = int("count") ~ get[Option[Int]]("tasks.status") map {
        case count ~ status => status.getOrElse(0) -> count
      }
      SQL"""SELECT COUNT(*) as count, status FROM tasks WHERE parent_id = $id GROUP BY status"""
        .as(summaryParser.*).toMap
    }
  }

  /**
    * Will run through the tasks in batches of 50 and update the priorities based on the rules
    * of the challenge
    *
    * @param user The user executing the request
    * @param id The id of the challenge
    * @param c The connection for the request
    */
  def updateTaskPriorities(user:User)(implicit id:Long, c:Option[Connection]=None) : Unit = {
    this.permission.hasWriteAccess(TaskType(), user)
    this.withMRConnection { implicit c =>
      val challenge = this.retrieveById(id) match {
        case Some(c) => c
        case None => throw new NotFoundException(s"Could not update priorties for tasks, no challenge with id $id found.")
      }
      // make sure that at least one of the challenges is valid
      if (Challenge.isValidRule(challenge.highPriorityRule) ||
          Challenge.isValidRule(challenge.mediumPriorityRule) ||
          Challenge.isValidRule(challenge.lowPriorityRule)) {
        var pointer = 0
        var currentTasks:List[Task] = List.empty
        do {
          currentTasks = listChildren(ChallengeDAL.DEFAULT_NUM_CHILDREN_LIST, pointer)
          val highPriorityIDs = currentTasks.filter(_.getTaskPriority(challenge) == Challenge.PRIORITY_HIGH).map(_.id).mkString(",")
          val mediumPriorityIDs = currentTasks.filter(_.getTaskPriority(challenge) == Challenge.PRIORITY_MEDIUM).map(_.id).mkString(",")
          val lowPriorityIDs = currentTasks.filter(_.getTaskPriority(challenge) == Challenge.PRIORITY_LOW).map(_.id).mkString(",")

          if (highPriorityIDs.nonEmpty) {
            SQL"""UPDATE tasks SET priority = ${Challenge.PRIORITY_HIGH} WHERE id IN (#$highPriorityIDs)""".executeUpdate()
          }
          if (mediumPriorityIDs.nonEmpty) {
            SQL"""UPDATE tasks SET priority = ${Challenge.PRIORITY_MEDIUM} WHERE id IN (#$mediumPriorityIDs)""".executeUpdate()
          }
          if (lowPriorityIDs.nonEmpty) {
            SQL"""UPDATE tasks SET priority = ${Challenge.PRIORITY_LOW} WHERE id IN (#$lowPriorityIDs)""".executeUpdate()
          }

          pointer += 1
        } while (currentTasks.size == 50)
        this.taskDAL.clearCaches
      }
    }
  }

  /**
    * Lists the children of the parent, override the base functionality and includes the geojson
    * as part of the query so that it doesn't have to fetch it each and every time.
    *
    * @param limit limits the number of children to be returned
    * @param offset For paging, ie. the page number starting at 0
    * @param id The parent ID
    * @return A list of children objects
    */
  override def listChildren(limit:Int=Config.DEFAULT_LIST_SIZE, offset:Int=0, onlyEnabled:Boolean=false, searchString:String="",
                            orderColumn:String="id", orderDirection:String="ASC")(implicit id:Long, c:Option[Connection]=None) : List[Task] = {
    // add a child caching option that will keep a list of children for the parent
    this.withMRConnection { implicit c =>
      // slightly different from the standard parser, in that it retrieves the geojson within the sql
      val geometryParser: RowParser[Task] = {
        get[Long]("tasks.id") ~
          get[String]("tasks.name") ~
          get[Long]("parent_id") ~
          get[Option[String]]("tasks.instruction") ~
          get[Option[String]]("location") ~
          get[String]("geometry") ~
          get[Option[Int]]("tasks.status") ~
          get[Int]("tasks.priority") map {
          case id ~ name ~ parent_id ~ instruction ~ location ~ geometry ~ status ~ priority =>
            Task(id, name, parent_id, instruction, location, geometry, status, priority)
        }
      }

      val query = s"""SELECT ${taskDAL.retrieveColumns},
                        (SELECT row_to_json(fc)::text as geometries
                          FROM ( SELECT 'FeatureCollection' As type, array_to_json(array_agg(f)) As features
                                  FROM ( SELECT 'Feature' As type,
                                                ST_AsGeoJSON(lg.geom)::json As geometry,
                                                hstore_to_json(lg.properties) As properties
                                        FROM task_geometries As lg
                                        WHERE task_id = tasks.id
                                  ) As f
                          ) As fc)::text AS geometry FROM tasks
                      WHERE parent_id = {id} ${this.enabled(onlyEnabled)}
                      ${this.searchField("name")}
                      ${this.order(Some(orderColumn), orderDirection)}
                      LIMIT ${this.sqlLimit(limit)} OFFSET {offset}"""
      SQL(query).on('ss -> this.search(searchString),
        'id -> ParameterValue.toParameterValue(id)(p = keyToStatement),
        'offset -> offset)
        .as(geometryParser.*)
    }
  }
}

object ChallengeDAL {
  val DEFAULT_NUM_CHILDREN_LIST = 1000
}
