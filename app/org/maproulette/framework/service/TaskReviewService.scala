/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.service

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration
import org.joda.time.DateTime

import org.maproulette.framework.model._
import org.maproulette.framework.psql.{Query, _}
import org.maproulette.framework.psql.filter.{BaseParameter, _}
import org.maproulette.framework.repository.{
  TaskReviewRepository,
  TaskRepository,
  TaskClusterRepository
}
import org.maproulette.framework.mixins.ReviewSearchMixin
import org.maproulette.exception.InvalidException
import org.maproulette.session.SearchParameters
import org.maproulette.permissions.Permission
import org.maproulette.provider.websockets.{WebSocketMessages, WebSocketProvider}
import org.maproulette.data.ChallengeType

// deprecated and will be removed as they are converted
import org.maproulette.models.{Task, TaskCluster}
import org.maproulette.models.dal.TaskBundleDAL

/**
  * Service layer for TaskReview
  *
  * @author krotstan
  */
@Singleton
class TaskReviewService @Inject() (
    repository: TaskReviewRepository,
    serviceManager: ServiceManager,
    taskBundleDAL: TaskBundleDAL,
    taskRepository: TaskRepository,
    taskClusterRepository: TaskClusterRepository,
    permission: Permission,
    webSocketProvider: WebSocketProvider
) extends ReviewSearchMixin {

  /**
    * Gets a Task object with review data
    *
    * @param taskId
    */
  def getTaskWithReview(taskId: Long): TaskWithReview = {
    this.repository.getTaskWithReview(taskId)
  }

  /**
    * Gets and claims a task for review.
    *
    * @param user        The user executing the request
    * @param primaryTask id of task that you wish to start/claim
    * @return task
    */
  def startTaskReview(user: User, primaryTask: Task): Option[Task] = {
    if (primaryTask.review.reviewClaimedBy.getOrElse(null) != null &&
        primaryTask.review.reviewClaimedBy.getOrElse(null) != user.id.toLong) {
      throw new InvalidException("This task is already being reviewed by someone else.")
    }

    // Each task in bundle must be claimed
    var taskList = List(primaryTask)
    if (primaryTask.isBundlePrimary.getOrElse(false)) {
      primaryTask.bundleId match {
        case Some(bId) =>
          this.taskBundleDAL.getTaskBundle(user, bId).tasks match {
            case Some(tList) =>
              taskList = tList
            case None => // do nothing -- just use our current task
          }
        case None => // no bundle id, do nothing
      }
    }

    this.repository.claimTaskReview(taskList, user)
    val updatedTask = this.getTaskWithReview(primaryTask.id)

    webSocketProvider.sendMessage(
      WebSocketMessages.reviewClaimed(
        WebSocketMessages.ReviewData(updatedTask)
      )
    )

    Option(updatedTask.task)
  }

  /**
    * Releases a claim on a task for review.
    *
    * @param user The user executing the request
    * @param task id of task that you wish to release
    * @return task
    */
  def cancelTaskReview(user: User, task: Task): Option[Task] = {
    if (task.review.reviewClaimedBy.getOrElse(null) != user.id.toLong) {
      throw new InvalidException("This task is not currently being reviewed by you.")
    }
    // Unclaim bundle?????
    this.repository.unclaimTaskReview(task, user)

    val updatedTask = this.getTaskWithReview(task.id)

    webSocketProvider.sendMessage(
      WebSocketMessages.reviewUpdate(
        WebSocketMessages.ReviewData(updatedTask)
      )
    )

    Option(updatedTask.task)
  }

  /**
    * Marks expired taskReviews as unnecessary.
    *
    * @param duration - age of task reviews to treat as 'expired'
    * @return The number of taskReviews that were expired
    */
  def expireTaskReviews(duration: FiniteDuration): Int = {
    this.repository.expireTaskReviews(duration)
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
  ): Option[Task] = {
    // If User is not a reviewer, then there is no next task to review
    if (!user.settings.isReviewer.getOrElse(false) && !permission.isSuperUser(user)) {
      return None
    }

    val position = lastTaskId match {
      case Some(taskId) => {
        // Queries for a map of (taskId -> row position)
        val rowMap = this.repository.queryTasksWithRowNumber(
          getReviewRequestedQueries(
            user,
            searchParameters,
            onlySaved,
            Paging(-1, 0),
            sort,
            order,
            false,
            excludeOtherReviewers
          )
        )

        // If our task id is in the row map, we have a position for it
        rowMap.get(taskId) match {
          case Some(row) => row
          case _         => 0
        }
      }
      case _ => 0
    }

    this.repository
      .queryTasks(
        getReviewRequestedQueries(
          user,
          searchParameters,
          onlySaved,
          Paging(1, position),
          sort,
          order,
          false,
          excludeOtherReviewers
        )
      )
      .headOption
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
      paging: Paging = Paging(-1, 0),
      sort: String,
      order: String,
      includeDisputed: Boolean = true,
      excludeOtherReviewers: Boolean = false
  ): (Int, List[Task]) = {
    // If User is not a reviewer, then there is no next task to review
    if (!user.settings.isReviewer.getOrElse(false) && !permission.isSuperUser(user)) {
      return (0, List[Task]())
    }

    val query =
      getReviewRequestedQueries(
        user,
        searchParameters,
        onlySaved,
        paging,
        sort,
        order,
        includeDisputed,
        excludeOtherReviewers
      )

    (this.repository.queryTaskCount(query), this.repository.queryTasks(query))
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
  ): List[Task] = {
    val order = Order(
      List(
        OrderField(
          s"""
            ST_Distance(tasks.location, (SELECT location FROM tasks WHERE id = $proximityId)),
            tasks.status, RANDOM()
          """,
          table = Some(""),
          isColumn = false
        )
      )
    )

    var query = this.setupReviewSearchClause(
      Query.simple(List(), order = order, paging = Paging(limit, 0)),
      user,
      permission,
      searchParameters,
      REVIEW_REQUESTED_TASKS,
      true,
      onlySaved,
      excludeOtherReviewers
    )

    query = addClaimedByFilter(query, user.id)
    query = query.addFilterGroup(
      FilterGroup(
        List(
          BaseParameter(
            Task.FIELD_ID,
            proximityId,
            Operator.NE,
            table = Some("tasks")
          ),
          BaseParameter(
            "id",
            "",
            Operator.NULL,
            table = Some("l")
          )
        )
      )
    )

    this.repository.queryTasksWithLocked(query)
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
  ): (Int, List[Task]) = {
    // If User is not a reviewer, then there is no next task to review
    if (!user.settings.isReviewer.getOrElse(false) && !permission.isSuperUser(user)) {
      return (0, List[Task]())
    }

    var query = setupReviewSearchClause(
      Query.simple(List(), order = buildOrdering(sort, order), paging = Paging(limit, offset)),
      user,
      permission,
      searchParameters,
      if (allowReviewNeeded) ALL_REVIEWED_TASKS else MY_REVIEWED_TASKS
    )

    (this.repository.queryTaskCount(query), this.repository.queryTasks(query))
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
      numberOfPoints: Int = this.taskClusterRepository.DEFAULT_NUMBER_OF_POINTS,
      onlySaved: Boolean = false,
      excludeOtherReviewers: Boolean = false
  ): List[TaskCluster] = {
    var query = setupReviewSearchClause(
      Query.simple(List()),
      user,
      permission,
      params,
      reviewTasksType,
      true,
      onlySaved,
      excludeOtherReviewers
    )

    val reviewField = reviewTasksType match {
      case MY_REVIEWED_TASKS    => TaskReview.FIELD_REVIEW_REQUESTED_BY
      case REVIEWED_TASKS_BY_ME => TaskReview.FIELD_REVIEWED_BY
      case _                    => null
    }

    if (reviewField != null) {
      query = query.addFilterGroup(
        FilterGroup(
          List(
            BaseParameter(
              reviewField,
              user.id,
              Operator.EQ,
              table = Some(TaskReview.TABLE)
            )
          )
        )
      )
    }

    this.taskClusterRepository.queryTaskClusters(query, numberOfPoints, params)
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
  ): Int = {
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

    var fetchBy = "reviewed_by"

    val isDisputed = task.review.reviewStatus.getOrElse(-1) != Task.REVIEW_STATUS_DISPUTED &&
      reviewStatus == Task.REVIEW_STATUS_DISPUTED
    val needsReReview = (task.review.reviewStatus.getOrElse(-1) != Task.REVIEW_STATUS_REQUESTED &&
      reviewStatus == Task.REVIEW_STATUS_REQUESTED) || isDisputed

    var reviewedBy          = task.review.reviewedBy
    var reviewRequestedBy   = task.review.reviewRequestedBy
    var additionalReviewers = task.review.additionalReviewers

    // Make sure we have an updated claimed at time.
    val reviewClaimedAt = this.getTaskWithReview(task.id).task.review.reviewClaimedAt

    // If the original reviewer is not the same as the user asking for this
    // review status change than we have a "meta-review" situation. Let's leave
    // the original reviewer as the reviewedBy on the task. The user will
    // still be noted as a reviewer in the task_review_history
    val originalReviewer =
      if (reviewedBy != None && reviewedBy.get != user.id) {
        if (!needsReReview) {
          // Add reviewer to the additionalReviewers if not the original
          if (additionalReviewers == None) {
            additionalReviewers = Some(List())
          }
          if (!additionalReviewers.contains(user.id) && !needsReReview) {
            additionalReviewers = Some(additionalReviewers.get :+ user.id)
          }
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

    val updatedRows = this.repository.updateTaskReview(
      user,
      task,
      reviewStatus,
      fetchBy,
      fetchByUser,
      additionalReviewers
    )

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
          this.repository.insertTaskReviewHistory(
            task,
            user.id,
            task.review.reviewedBy.get,
            None,
            reviewStatus,
            null
          )

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
          this.repository.insertTaskReviewHistory(
            task,
            task.review.reviewRequestedBy.get,
            user.id,
            if (originalReviewer.getOrElse(0) != user.id) Some(originalReviewer.get)
            else None,
            reviewStatus,
            reviewClaimedAt.getOrElse(null)
          )

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
        this.repository.insertTaskReviewHistory(
          task,
          user.id,
          task.review.reviewedBy.get,
          None,
          reviewStatus,
          null
        )
      }
    }

    this.taskRepository.cacheManager.withOptionCaching { () =>
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
    }

    updatedRows
  }

  private def buildOrdering(sort: String, order: String): Order = {
    sort match {
      case sortColumn if sortColumn.nonEmpty =>
        // We have two "id" columns: one for Tasks and one for taskReview. So
        // we need to specify which one to sort by for the SQL query.
        val table     = if (sortColumn == "id" || sortColumn == "status") Some("tasks") else Some("")
        val direction = if (order == "DESC") Order.DESC else Order.ASC

        Order(
          List(
            OrderField(
              name = sortColumn,
              direction = direction,
              table = table
            )
          )
        )
      case _ => Order()
    }
  }

  private def getReviewRequestedQueries(
      user: User,
      searchParameters: SearchParameters,
      onlySaved: Boolean = false,
      paging: Paging = Paging(-1, 0),
      sort: String,
      order: String,
      includeDisputed: Boolean = true,
      excludeOtherReviewers: Boolean = false
  ): Query = {

    val query = this.setupReviewSearchClause(
      Query.simple(List(), order = this.buildOrdering(sort, order), paging = paging),
      user,
      permission,
      searchParameters,
      REVIEW_REQUESTED_TASKS,
      includeDisputed,
      onlySaved,
      excludeOtherReviewers
    )

    addClaimedByFilter(query, user.id)
  }

  private def addClaimedByFilter(query: Query, userId: Long): Query = {
    // Unclaimed or claimed by me for review
    // "(task_review.review_claimed_at IS NULL OR task_review.review_claimed_by = ${user.id})"
    query.addFilterGroup(
      FilterGroup(
        List(
          BaseParameter(
            TaskReview.FIELD_REVIEW_CLAIMED_BY,
            None,
            Operator.NULL,
            table = Some(TaskReview.TABLE)
          ),
          BaseParameter(
            TaskReview.FIELD_REVIEW_CLAIMED_BY,
            userId,
            Operator.EQ,
            table = Some(TaskReview.TABLE)
          )
        ),
        OR()
      )
    )
  }
}
