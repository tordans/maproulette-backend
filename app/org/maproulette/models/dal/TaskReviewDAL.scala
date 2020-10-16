/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package org.maproulette.models.dal

import java.sql.Connection

import anorm.JodaParameterMetaData._
import anorm.SqlParser._
import anorm._
import javax.inject.{Inject, Provider, Singleton}
import org.joda.time.DateTime
import org.maproulette.Config
import org.maproulette.data.{ProjectType, ChallengeType, UserType}
import org.maproulette.exception.InvalidException
import org.maproulette.framework.model
import org.maproulette.framework.model.{ReviewMetrics, TaskReview, TaskWithReview, User}
import org.maproulette.framework.service.{ServiceManager, TagService}
import org.maproulette.models._
import org.maproulette.permissions.Permission
import org.maproulette.provider.websockets.{WebSocketMessages, WebSocketProvider}
import org.maproulette.session.SearchParameters
import play.api.db.Database
import play.api.libs.ws.WSClient

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
  * @author krotstan
  */
@Singleton
class TaskReviewDAL @Inject() (
    override val db: Database,
    override val tagService: TagService,
    override val permission: Permission,
    serviceManager: ServiceManager,
    config: Config,
    dalManager: Provider[DALManager],
    webSocketProvider: WebSocketProvider,
    ws: WSClient
) extends TaskDAL(
      db,
      tagService,
      permission,
      serviceManager,
      config,
      dalManager,
      webSocketProvider,
      ws
    ) {

  val REVIEW_REQUESTED_TASKS = 1 // Tasks needing to be reviewed
  val MY_REVIEWED_TASKS      = 2 // Tasks reviewed by user
  val REVIEWED_TASKS_BY_ME   = 3 // Tasks completed by user and done review
  val ALL_REVIEWED_TASKS     = 4 // All review(ed) tasks

  implicit val taskWithReviewParser: RowParser[TaskWithReview] = {
    get[Long]("tasks.id") ~
      get[String]("tasks.name") ~
      get[DateTime]("tasks.created") ~
      get[DateTime]("tasks.modified") ~
      get[Long]("parent_id") ~
      get[Option[String]]("tasks.instruction") ~
      get[Option[String]]("geo_location") ~
      get[Option[Int]]("tasks.status") ~
      get[Option[String]]("geo_json") ~
      get[Option[String]]("cooperative_work") ~
      get[Option[DateTime]]("tasks.mapped_on") ~
      get[Option[Long]]("tasks.completed_time_spent") ~
      get[Option[Long]]("tasks.completed_by") ~
      get[Option[Int]]("task_review.review_status") ~
      get[Option[Long]]("task_review.review_requested_by") ~
      get[Option[Long]]("task_review.reviewed_by") ~
      get[Option[DateTime]]("task_review.reviewed_at") ~
      get[Option[DateTime]]("task_review.review_started_at") ~
      get[Option[Long]]("task_review.review_claimed_by") ~
      get[Option[DateTime]]("task_review.review_claimed_at") ~
      get[Option[List[Long]]]("task_review.additional_reviewers") ~
      get[Int]("tasks.priority") ~
      get[Option[Long]]("tasks.changeset_id") ~
      get[Option[Long]]("tasks.bundle_id") ~
      get[Option[Boolean]]("tasks.is_bundle_primary") ~
      get[Option[String]]("challenge_name") ~
      get[Option[String]]("review_requested_by_username") ~
      get[Option[String]]("reviewed_by_username") ~
      get[Option[String]]("responses") map {
      case id ~ name ~ created ~ modified ~ parent_id ~ instruction ~ location ~ status ~ geojson ~
            cooperativeWork ~ mappedOn ~ completedTimeSpent ~ completedBy ~ reviewStatus ~ reviewRequestedBy ~
            reviewedBy ~ reviewedAt ~ reviewStartedAt ~ reviewClaimedBy ~ reviewClaimedAt ~ additionalReviewers ~
            priority ~ changesetId ~ bundleId ~ isBundlePrimary ~ challengeName ~ reviewRequestedByUsername ~
            reviewedByUsername ~ responses =>
        val values = this.updateAndRetrieve(id, geojson, location, cooperativeWork)
        model.TaskWithReview(
          Task(
            id,
            name,
            created,
            modified,
            parent_id,
            instruction,
            values._2,
            values._1,
            values._3,
            status,
            mappedOn,
            completedTimeSpent,
            completedBy,
            TaskReviewFields(
              reviewStatus,
              reviewRequestedBy,
              reviewedBy,
              reviewedAt,
              reviewStartedAt,
              reviewClaimedBy,
              reviewClaimedAt,
              additionalReviewers
            ),
            priority,
            changesetId,
            responses,
            bundleId,
            isBundlePrimary
          ),
          TaskReview(
            -1,
            id,
            reviewStatus,
            challengeName,
            reviewRequestedBy,
            reviewRequestedByUsername,
            reviewedBy,
            reviewedByUsername,
            reviewedAt,
            reviewStartedAt,
            additionalReviewers,
            reviewClaimedBy,
            None,
            None
          )
        )
    }
  }

  /**
    * Gets and claims a task for review.
    *
    * @param user        The user executing the request
    * @param primaryTask id of task that you wish to start/claim
    * @return task
    */
  def startTaskReview(user: User, primaryTask: Task)(
      implicit c: Connection = null
  ): Option[Task] = {
    if (primaryTask.review.reviewClaimedBy.getOrElse(null) != null &&
        primaryTask.review.reviewClaimedBy.getOrElse(null) != user.id.toLong) {
      throw new InvalidException("This task is already being reviewed by someone else.")
    }

    var taskList = List(primaryTask)
    if (primaryTask.isBundlePrimary.getOrElse(false)) {
      primaryTask.bundleId match {
        case Some(bId) =>
          this.manager.taskBundle.getTaskBundle(user, bId).tasks match {
            case Some(tList) =>
              taskList = tList
            case None => // do nothing -- just use our current task
          }
        case None => // no bundle id, do nothing
      }
    }

    this.withMRTransaction { implicit c =>
      // Unclaim everything before starting a new task.
      SQL"""UPDATE task_review SET review_claimed_by = NULL, review_claimed_at = NULL
              WHERE review_claimed_by = #${user.id}""".executeUpdate()

      for (task <- taskList) {
        SQL"""UPDATE task_review SET review_claimed_by = #${user.id}, review_claimed_at = NOW()
                    WHERE task_id = #${task.id} AND review_claimed_at IS NULL""".executeUpdate()

        try {
          this.lockItem(user, task)
        } catch {
          case e: Exception => logger.warn(e.getMessage)
        }

        implicit val id = task.id
        this.manager.task.cacheManager.withUpdatingCache(Long => this.retrieveById) {
          implicit cachedItem =>
            val result =
              Some(task.copy(review = task.review.copy(reviewClaimedBy = Option(user.id.toInt))))
            result
        }(task.id, true, true)
      }
    }

    webSocketProvider.sendMessage(
      WebSocketMessages.reviewClaimed(
        WebSocketMessages.ReviewData(this.getTaskWithReview(primaryTask.id))
      )
    )

    val updatedTask =
      primaryTask.copy(review = primaryTask.review.copy(reviewClaimedBy = Option(user.id.toInt)))

    this.manager.task.cacheManager.withOptionCaching { () =>
      Some(updatedTask)
    }
    Option(updatedTask)
  }

  /**
    * Releases a claim on a task for review.
    *
    * @param user The user executing the request
    * @param task id of task that you wish to release
    * @return task
    */
  def cancelTaskReview(user: User, task: Task)(implicit c: Connection = null): Option[Task] = {
    if (task.review.reviewClaimedBy.getOrElse(null) != user.id.toLong) {
      throw new InvalidException("This task is not currently being reviewed by you.")
    }

    this.withMRTransaction { implicit c =>
      val updatedRows =
        SQL"""UPDATE task_review SET review_claimed_by = NULL, review_claimed_at = NULL
              WHERE task_review.task_id = #${task.id}""".executeUpdate()

      // if returning 0, then this is because the item is locked by a different user
      if (updatedRows == 0) {
        throw new IllegalAccessException(
          s"Current task [${task.id} is locked by another user, cannot cancel review at this time."
        )
      }

      webSocketProvider.sendMessage(
        WebSocketMessages.reviewUpdate(
          WebSocketMessages.ReviewData(this.getTaskWithReview(task.id))
        )
      )
    }

    try {
      this.unlockItem(user, task)
    } catch {
      case e: Exception => logger.warn(e.getMessage)
    }

    val updatedTask = task.copy(review = task.review.copy(reviewClaimedBy = None))
    this.manager.task.cacheManager.withOptionCaching { () =>
      Some(updatedTask)
    }
    Option(updatedTask)
  }

  /**
    * Gets and claims the next task for review.
    *
    * @param user The user executing the request
    * @param searchParameters
    * @param onlySaved
    * @param sort
    * @param order
    * @param lastTaskId
    * @param excludeOtherReviewers
    * @return task
    */
  def nextTaskReview(
      user: User,
      searchParameters: SearchParameters,
      onlySaved: Boolean = false,
      sort: String,
      order: String,
      lastTaskId: Option[Long] = None,
      excludeOtherReviewers: Boolean = false
  )(implicit c: Connection = null): Option[Task] = {
    var position = 0

    lastTaskId match {
      case Some(taskId) => {
        val (countAll, queryAll, parametersAll) =
          _getReviewRequestedQueries(
            user,
            searchParameters,
            onlySaved,
            -1,
            0,
            sort,
            order,
            false,
            excludeOtherReviewers
          )

        // This only happens if a non-reviewer is trying to do get a review task
        if (queryAll == null) {
          return None
        }

        val rowMap = mutable.Map[Long, Int]()
        val rowNumParser: RowParser[Long] = {
          get[Int]("row_num") ~
            get[Long]("tasks.id") map {
            case row ~ id => {
              rowMap.put(id, row)
              id
            }
          }
        }

        this.withMRTransaction { implicit c =>
          sqlWithParameters(queryAll, parametersAll).as(rowNumParser.*)

          val rowPosition = rowMap.get(taskId)
          rowPosition match {
            case Some(row) =>
              position = row // fetch next task (offset starts at 0 but rows at 1)
            case _ => // not found so do nothing
          }
        }
      }
      case _ => // do nothing
    }

    val (countQuery, query, parameters) =
      _getReviewRequestedQueries(
        user,
        searchParameters,
        onlySaved,
        1,
        position,
        sort,
        order,
        false,
        excludeOtherReviewers
      )

    // This only happens if a non-reviewer is trying to do get a review task
    if (query == null) {
      None
    } else {
      this.withMRTransaction { implicit c =>
        sqlWithParameters(query, parameters).as(this.parser.*).headOption
      }
    }
  }

  /**
    * Retrieve tasks geographically closest to the given task id wit the
    * given set of search parameters.
    */
  def getNearbyReviewTasks(
      user: User,
      searchParameters: SearchParameters,
      proximityId: Long,
      limit: Int = 5,
      excludeOtherReviewers: Boolean = false,
      onlySaved: Boolean = false
  )(implicit c: Option[Connection] = None): List[Task] = {
    val (parameters, joinClause, whereClause) = setupReviewSearchClause(
      user,
      searchParameters,
      REVIEW_REQUESTED_TASKS,
      false,
      onlySaved,
      excludeOtherReviewers
    )

    this.appendInWhereClause(
      whereClause,
      s"(task_review.review_claimed_at IS NULL OR task_review.review_claimed_by = ${user.id})"
    )

    val query =
      s"""SELECT tasks.$retrieveColumnsWithReview FROM tasks
      LEFT JOIN locked l ON l.item_id = tasks.id
      ${joinClause}
      WHERE tasks.id <> $proximityId AND
            l.id IS NULL AND
            ${whereClause}
      ORDER BY ST_Distance(tasks.location, (SELECT location FROM tasks WHERE id = $proximityId)), tasks.status, RANDOM()
      LIMIT ${this.sqlLimit(limit)}"""

    this.withMRTransaction { implicit c =>
      sqlWithParameters(query, parameters).as(this.parser.*)
    }
  }

  /**
    * Gets a list of tasks that have requested review (and are in this user's project group)
    *
    * @param user            The user executing the request
    * @param limit           The number of tasks to return
    * @param offset          Offset to start paging
    * @param sort            Sort column
    * @param order           DESC or ASC
    * @param includeDisputed Whether disputed tasks whould be returned in results (default is true)
    * @return A list of tasks
    */
  def getReviewRequestedTasks(
      user: User,
      searchParameters: SearchParameters,
      onlySaved: Boolean = false,
      limit: Int = -1,
      offset: Int = 0,
      sort: String,
      order: String,
      includeDisputed: Boolean = true,
      excludeOtherReviewers: Boolean = false
  )(implicit c: Connection = null): (Int, List[Task]) = {
    val (countQuery, query, parameters) =
      _getReviewRequestedQueries(
        user,
        searchParameters,
        onlySaved,
        limit,
        offset,
        sort,
        order,
        includeDisputed,
        excludeOtherReviewers
      )

    // This only happens if a non-reviewer is trying to get review tasks
    if (query == null) {
      (0, List[Task]())
    } else {
      var count = 0
      val tasks = this.manager.task.cacheManager.withIDListCaching { implicit cachedItems =>
        this.withMRTransaction { implicit c =>
          count = sqlWithParameters(countQuery, parameters).as(SqlParser.int("count").single)
          sqlWithParameters(query, parameters).as(this.parser.*)
        }
      }
      (count, tasks)
    }
  }

  def _getReviewRequestedQueries(
      user: User,
      searchParameters: SearchParameters,
      onlySaved: Boolean = false,
      limit: Int = -1,
      offset: Int = 0,
      sort: String,
      order: String,
      includeDisputed: Boolean = true,
      excludeOtherReviewers: Boolean = false
  )(implicit c: Connection = null): (String, String, ListBuffer[NamedParameter]) = {
    var orderByClause = ""

    val (parameters, joinClause, whereClause) = setupReviewSearchClause(
      user,
      searchParameters,
      REVIEW_REQUESTED_TASKS,
      includeDisputed,
      onlySaved,
      excludeOtherReviewers
    )

    // Unclaimed or claimed by me for review
    this.appendInWhereClause(
      whereClause,
      s"(task_review.review_claimed_at IS NULL OR task_review.review_claimed_by = ${user.id})"
    )

    sort match {
      case s if s.nonEmpty =>
        var sortColumn = s
        // We have two "id" columns: one for Tasks and one for taskReview. So
        // we need to specify which one to sort by for the SQL query.
        if (s == "id") {
          sortColumn = "tasks." + s
        }
        orderByClause = this.order(Some(sortColumn), order, "", false)
      case _ => // ignore
    }

    val query =
      if (user.settings.isReviewer.getOrElse(false) || permission.isSuperUser(user)) {
        s"""
            SELECT ROW_NUMBER() OVER (${orderByClause}) as row_num,
              tasks.${this.retrieveColumnsWithReview} FROM tasks
            ${joinClause}
            WHERE
            ${whereClause}
            ${orderByClause}
            LIMIT ${sqlLimit(limit)} OFFSET ${offset}
           """
      } else {
        return (null, null, null)
      }

    val countQuery =
      s"""
          SELECT count(*) FROM tasks
          ${joinClause}
          WHERE ${whereClause}
        """

    (countQuery, query, parameters)
  }

  /**
    * Gets a list of tasks that have been reviewed (either by this user or requested by this user)
    *
    * @param user              The user executing the request
    * @param allowReviewNeeded Whether we should include review requested tasks as well
    * @param limit             The amount of tasks to be returned
    * @param offset            Offset to start paging
    * @param sort              Column to sort
    * @param order             DESC or ASC
    * @return A list of tasks
    */
  def getReviewedTasks(
      user: User,
      searchParameters: SearchParameters,
      allowReviewNeeded: Boolean = false,
      limit: Int = -1,
      offset: Int = 0,
      sort: String,
      order: String
  )(implicit c: Connection = null): (Int, List[Task]) = {
    var orderByClause = ""

    val (parameters, joinClause, whereClause) = setupReviewSearchClause(
      user,
      searchParameters,
      if (allowReviewNeeded) ALL_REVIEWED_TASKS else MY_REVIEWED_TASKS
    )

    sort match {
      case s if s.nonEmpty =>
        orderByClause = this.order(Some(s), order, "", false)
      case _ => // ignore
    }

    val query =
      if (user.settings.isReviewer.getOrElse(false) || permission.isSuperUser(user)) {
        s"""
          SELECT tasks.${this.retrieveColumnsWithReview} FROM tasks
          ${joinClause}
          WHERE
          ${whereClause}
          ${orderByClause}
          LIMIT ${sqlLimit(limit)} OFFSET ${offset}
         """
      } else {
        return (0, List[Task]())
      }

    val countQuery =
      s"""
        SELECT count(*) FROM tasks
        ${joinClause}
        WHERE ${whereClause}
      """

    var count = 0
    val tasks = this.manager.task.cacheManager.withIDListCaching { implicit cachedItems =>
      this.withMRTransaction { implicit c =>
        count = sqlWithParameters(countQuery, parameters).as(SqlParser.int("count").single)
        sqlWithParameters(query, parameters).as(this.parser.*)
      }
    }

    (count, tasks)
  }

  /**
    * Retrieves task clusters for review criteria
    *
    * @param user
    * @param reviewTasksType
    * @param params         SearchParameters used to filter the tasks in the cluster
    * @param numberOfPoints Number of cluster points to group all the tasks by
    * @param c              an implicit connection
    * @return A list of task clusters
    */
  def getReviewTaskClusters(
      user: User,
      reviewTasksType: Int,
      params: SearchParameters,
      numberOfPoints: Int = TaskDAL.DEFAULT_NUMBER_OF_POINTS,
      onlySaved: Boolean = false,
      excludeOtherReviewers: Boolean = false
  )(implicit c: Option[Connection] = None): List[TaskCluster] = {
    this.withMRConnection { implicit c =>
      val (parameters, joinClause, whereClause) = setupReviewSearchClause(
        user,
        params,
        reviewTasksType,
        true,
        onlySaved,
        excludeOtherReviewers
      )

      reviewTasksType match {
        case MY_REVIEWED_TASKS =>
          this.appendInWhereClause(whereClause, s"task_review.reviewed_by=${user.id}")
        case REVIEWED_TASKS_BY_ME =>
          this.appendInWhereClause(whereClause, s"task_review.review_requested_by=${user.id}")
        case _ => // do nothing
      }

      val query =
        this.manager.taskCluster
          .getTaskClusterQuery(joinClause.toString, "where " + whereClause.toString, numberOfPoints)
      val result = sqlWithParameters(query, parameters).as(
        this.manager.taskCluster.getTaskClusterParser(params).*
      )
      // Filter out invalid clusters.
      result.filter(_ != None).asInstanceOf[List[TaskCluster]]
    }
  }

  /**
    * private setup the search clauses for searching the review tables
    * @deprecated  Please use new method in SearchReviewMixin when converting to new system
    */
  private def setupReviewSearchClause(
      user: User,
      searchParameters: SearchParameters,
      reviewTasksType: Int = ALL_REVIEWED_TASKS,
      includeDisputed: Boolean = true,
      onlySaved: Boolean = false,
      excludeOtherReviewers: Boolean = false
  ): (ListBuffer[NamedParameter], StringBuilder, StringBuilder) = {
    val whereClause = new StringBuilder()
    val joinClause  = new StringBuilder("INNER JOIN challenges c ON c.id = tasks.parent_id ")
    joinClause ++= "LEFT OUTER JOIN task_review ON task_review.task_id = tasks.id "
    joinClause ++= "INNER JOIN projects p ON p.id = c.parent_id "

    // Check for bundles
    this.appendInWhereClause(
      whereClause,
      "(tasks.bundle_id is NULL OR tasks.is_bundle_primary = true)"
    )

    // Ignore unnecessary
    this.appendInWhereClause(
      whereClause,
      s"task_review.review_status != ${Task.REVIEW_STATUS_UNNECESSARY}"
    )

    if (reviewTasksType == REVIEW_REQUESTED_TASKS) {
      // Limit to only review requested and disputed
      if (includeDisputed) {
        this.appendInWhereClause(
          whereClause,
          s"(task_review.review_status=${Task.REVIEW_STATUS_REQUESTED} OR " +
            s" task_review.review_status=${Task.REVIEW_STATUS_DISPUTED})"
        )
      } else {
        this.appendInWhereClause(
          whereClause,
          s"task_review.review_status=${Task.REVIEW_STATUS_REQUESTED}"
        )
      }

      // Only challenges 'saved' (marked as favorite) by this user
      if (onlySaved) {
        joinClause ++= "INNER JOIN saved_challenges sc ON sc.challenge_id = c.id "
        this.appendInWhereClause(whereClause, s"sc.user_id = ${user.id}")
      }

      // Don't show tasks already reviewed by someone else
      // Used most often when tasks need a "re-review" (so in a requested state)
      if (excludeOtherReviewers) {
        this.appendInWhereClause(
          whereClause,
          s"(task_review.reviewed_by IS NULL OR task_review.reviewed_by = ${user.id})"
        )
      }
    } else if (reviewTasksType == MY_REVIEWED_TASKS) {
      // Limit to already reviewed tasks
      this.appendInWhereClause(
        whereClause,
        s"task_review.review_status <> ${Task.REVIEW_STATUS_REQUESTED} "
      )
    }

    // Setup permissions
    // You see review task when:
    // 1. Project and Challenge enabled (so visible to everyone)
    // 2. You own the Project
    // 3. You manage the project (your user group matches groups of project)
    // 4. You worked on the task - ie. review/reviewed it
    if (reviewTasksType == REVIEW_REQUESTED_TASKS) {
      if (!permission.isSuperUser(user)) {
        // If not a super user than you are not allowed to request reviews
        // on tasks you completed.
        this.appendInWhereClause(
          whereClause,
          s""" ((p.enabled AND c.enabled) OR
                ${this.userHasProjectGrantSQL(user)}
               ) AND
               task_review.review_requested_by != ${user.id} """
        )
      }
    } else {
      if (!permission.isSuperUser(user)) {
        this.appendInWhereClause(
          whereClause,
          s"""((p.enabled AND c.enabled) OR
                ${this.userHasProjectGrantSQL(user)} OR
                task_review.review_requested_by = ${user.id} OR
                task_review.reviewed_by = ${user.id}
              )"""
        )
      }
    }

    searchParameters.taskParams.taskReviewStatus match {
      case Some(statuses) if statuses.nonEmpty =>
        val invert =
          if (searchParameters.invertFields.getOrElse(List()).contains("trStatus")) "NOT" else ""
        val statusClause = new StringBuilder(
          s"(task_review.review_status ${invert} IN (${statuses.mkString(",")}))"
        )
        this.appendInWhereClause(whereClause, statusClause.toString())
      case Some(statuses) if statuses.isEmpty => //ignore this scenario
      case _                                  =>
    }

    val parameters = new ListBuffer[NamedParameter]()
    parameters ++= addSearchToQuery(searchParameters, whereClause)

    this.paramsOwner(searchParameters, whereClause)
    this.paramsReviewer(searchParameters, whereClause)
    this.paramsMapper(searchParameters, whereClause)
    this.paramsTaskStatus(searchParameters, whereClause, List())

    this.paramsMappers(searchParameters, whereClause)
    this.paramsReviewers(searchParameters, whereClause)
    this.paramsTaskPriorities(searchParameters, whereClause)

    this.paramsLocation(searchParameters, whereClause)
    this.paramsProjectSearch(searchParameters, whereClause)
    this.paramsTaskId(searchParameters, whereClause)
    this.paramsPriority(searchParameters, whereClause)
    this.paramsTaskTags(searchParameters, whereClause)
    this.paramsReviewDate(searchParameters, whereClause)

    (parameters, joinClause, whereClause)
  }

  /**
    * Sets the review status for a task. The user cannot set the status of a task unless the object has
    * been locked by the same user before hand.
    * Will throw an InvalidException if the task review status cannot be set due to the current review status
    * Will throw an IllegalAccessException if the user is a not a reviewer, or if the task is locked by
    * a different user.
    *
    * @param task         The task to set the status for
    * @param reviewStatus The review status to set
    * @param user         The user setting the status
    * @return The number of rows updated, should only ever be 1
    */
  def setTaskReviewStatus(
      task: Task,
      reviewStatus: Int,
      user: User,
      actionId: Option[Long],
      commentContent: String = ""
  )(implicit c: Connection = null): Int = {
    if (!permission.isSuperUser(user) && !user.settings.isReviewer.get && reviewStatus != Task.REVIEW_STATUS_REQUESTED &&
        reviewStatus != Task.REVIEW_STATUS_DISPUTED && reviewStatus != Task.REVIEW_STATUS_UNNECESSARY) {
      throw new IllegalAccessException("User must be a reviewer to edit task review status.")
    } else if (reviewStatus == Task.REVIEW_STATUS_UNNECESSARY) {
      if (task.review.reviewStatus.getOrElse(0) != Task.REVIEW_STATUS_REQUESTED) {
        // We should not update review status to Unnecessary unless the review status is requested
        return 0
      }

      this.permission.hasWriteAccess(ChallengeType(), user)(task.parent)
    }

    val updatedRows = this.withMRTransaction { implicit c =>
      var fetchBy = "reviewed_by"

      val isDisputed = task.review.reviewStatus.getOrElse(-1) != Task.REVIEW_STATUS_DISPUTED &&
        reviewStatus == Task.REVIEW_STATUS_DISPUTED
      val needsReReview = (task.review.reviewStatus.getOrElse(-1) != Task.REVIEW_STATUS_REQUESTED &&
        reviewStatus == Task.REVIEW_STATUS_REQUESTED) || isDisputed

      var reviewedBy          = task.review.reviewedBy
      var reviewRequestedBy   = task.review.reviewRequestedBy
      var additionalReviewers = task.review.additionalReviewers

      // Make sure we have an updated claimed at time.
      val reviewClaimedAt = getTaskWithReview(task.id).task.review.reviewClaimedAt

      // If the original reviewer is not the same as the user asking for this
      // review status change than we have a "meta-review" situation. Let's leave
      // the original reviewer as the reviewedBy on the task. The user will
      // still be noted as a reviewer in the task_review_history
      val originalReviewer =
        if (reviewedBy != None && reviewedBy.get != user.id) {
          if (additionalReviewers == None) {
            additionalReviewers = Some(List())
          }
          if (!additionalReviewers.contains(user.id)) {
            additionalReviewers = Some(additionalReviewers.get :+ user.id)
          }
          reviewedBy
        } else Some(user.id)

      // If we are changing the status back to "needsReview" then this task
      // has been fixed by the mapper and the mapper is requesting review again
      val fetchByUser =
        if (needsReReview) {
          fetchBy = "review_requested_by"
          reviewRequestedBy = Some(user.id)
          user.id
        } else {
          reviewedBy = Some(user.id)
          originalReviewer.get
        }

      val updatedRows =
        SQL"""UPDATE task_review SET review_status = $reviewStatus,
                                 #${fetchBy} = ${fetchByUser},
                                 reviewed_at = NOW(),
                                 review_started_at = task_review.review_claimed_at,
                                 review_claimed_at = NULL,
                                 review_claimed_by = NULL,
                                 additional_reviewers = #${additionalReviewers match {
          case Some(ar) => "ARRAY[" + ar.mkString(",") + "]"
          case None     => "NULL"
        }}
                             WHERE task_review.task_id = (
                                SELECT tasks.id FROM tasks
                                LEFT JOIN locked l on l.item_id = tasks.id AND l.item_type = ${task.itemType.typeId}
                                WHERE tasks.id = ${task.id} AND (l.user_id = ${user.id} OR l.user_id IS NULL)
                              )""".executeUpdate()
      // if returning 0, then this is because the item is locked by a different user
      if (updatedRows == 0) {
        throw new IllegalAccessException(
          s"Current task [${task.id} is locked by another user, cannot update review status at this time."
        )
      }

      // if you set the status successfully on a task you will lose the lock of that task
      try {
        this.unlockItem(user, task)
      } catch {
        case e: Exception => logger.warn(e.getMessage)
      }

      webSocketProvider.sendMessage(
        WebSocketMessages.reviewUpdate(
          WebSocketMessages.ReviewData(this.getTaskWithReview(task.id))
        )
      )

      val comment = commentContent.nonEmpty match {
        case true =>
          Some(this.serviceManager.comment.create(user, task.id, commentContent, actionId))
        case false => None
      }

      // Don't send a notification for every task in a task bundle, only the
      // primary task
      if (task.bundleId.isEmpty || task.isBundlePrimary.getOrElse(false)) {
        if (!isDisputed) {
          if (needsReReview) {
            // Let's note in the task_review_history table that this task needs review again
            SQL"""INSERT INTO task_review_history
                              (task_id, requested_by, reviewed_by, review_status, reviewed_at, review_started_at)
                  VALUES (${task.id}, ${user.id}, ${task.review.reviewedBy},
                          $reviewStatus, NOW(), NULL)""".executeUpdate()
            this.serviceManager.notification.createReviewNotification(
              user,
              task.review.reviewedBy.getOrElse(-1),
              reviewStatus,
              task,
              comment
            )
          } else {
            // Let's note in the task_review_history table that this task was reviewed
            // and also who the original reviewer was (assuming we have one)
            SQL"""INSERT INTO task_review_history
                              (task_id, requested_by, reviewed_by, review_status,
                               reviewed_at, review_started_at, original_reviewer)
                  VALUES (${task.id}, ${task.review.reviewRequestedBy}, ${user.id},
                          $reviewStatus, NOW(), ${reviewClaimedAt},
                          #${if (originalReviewer.getOrElse(0) != user.id) originalReviewer.get
            else "NULL"}
                         )""".executeUpdate()
            if (reviewStatus != Task.REVIEW_STATUS_UNNECESSARY) {
              this.serviceManager.notification.createReviewNotification(
                user,
                task.review.reviewRequestedBy.getOrElse(-1),
                reviewStatus,
                task,
                comment
              )

              // Let's let the original reviewer know that the review status
              // has been changed.
              if (originalReviewer.get != reviewedBy.get) {
                this.serviceManager.notification.createReviewRevisedNotification(
                  user,
                  originalReviewer.get,
                  reviewStatus,
                  task,
                  comment
                )
              }
            }
          }
        } else {
          // For disputed tasks.
          SQL"""INSERT INTO task_review_history
                            (task_id, requested_by, reviewed_by, review_status, reviewed_at, review_started_at)
                VALUES (${task.id}, ${user.id}, ${task.review.reviewedBy},
                        $reviewStatus, NOW(), NULL)""".executeUpdate()
        }
      }

      this.manager.task.cacheManager.withOptionCaching { () =>
        Some(
          task.copy(review = task.review.copy(
            reviewStatus = Some(reviewStatus),
            reviewRequestedBy = reviewRequestedBy,
            reviewedBy = reviewedBy,
            reviewedAt = Some(new DateTime())
          )
          )
        )
      }

      if (reviewStatus != Task.REVIEW_STATUS_UNNECESSARY) {
        if (!needsReReview) {
          var reviewStartTime: Option[Long] = None
          task.review.reviewClaimedAt match {
            case Some(t) =>
              reviewStartTime = Some(t.getMillis())
            case None => // do nothing
          }

          this.serviceManager.userMetrics.updateUserScore(
            None,
            None,
            Option(reviewStatus),
            task.review.reviewedBy != None,
            false,
            None,
            task.review.reviewRequestedBy.get
          )
          this.serviceManager.userMetrics.updateUserScore(
            None,
            None,
            Option(reviewStatus),
            false,
            true,
            reviewStartTime,
            user.id
          )
        } else if (reviewStatus == Task.REVIEW_STATUS_DISPUTED) {
          this.serviceManager.userMetrics.updateUserScore(
            None,
            None,
            Option(reviewStatus),
            true,
            true,
            None,
            task.review.reviewedBy.get
          )
          this.serviceManager.userMetrics.updateUserScore(
            None,
            None,
            Option(reviewStatus),
            true,
            false,
            None,
            task.review.reviewRequestedBy.get
          )
        }

        updatedRows
      }
      updatedRows
    }

    updatedRows
  }

  def getTaskWithReview(taskId: Long): TaskWithReview = {
    this.withMRConnection { implicit c =>
      val query =
        s"""
        SELECT $retrieveColumnsWithReview,
               challenges.name as challenge_name,
               mappers.name as review_requested_by_username,
               reviewers.name as reviewed_by_username
        FROM ${this.tableName}
        LEFT OUTER JOIN task_review ON task_review.task_id = tasks.id
        LEFT OUTER JOIN users mappers ON task_review.review_requested_by = mappers.id
        LEFT OUTER JOIN users reviewers ON task_review.reviewed_by = reviewers.id
        INNER JOIN challenges ON challenges.id = tasks.parent_id
        WHERE tasks.id = {taskId}
      """
      SQL(query).on(Symbol("taskId") -> taskId).as(this.taskWithReviewParser.single)
    }
  }

  private def userHasProjectGrantSQL(user: User): String =
    s"""p.id IN (${user.managedProjectIds().mkString(",")})"""
}
