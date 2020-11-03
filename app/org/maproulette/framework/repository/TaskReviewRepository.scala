/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.repository

import java.sql.Connection
import scala.concurrent.duration.FiniteDuration
import scala.collection.mutable

import anorm.SqlParser.get
import anorm.{ToParameterValue, SimpleSql, Row, SqlParser, RowParser, ~, SQL}
import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import org.maproulette.framework.model.{TaskReview, TaskWithReview, User}
import org.maproulette.framework.psql.{Query, Grouping, GroupField, Order, Paging}
import org.maproulette.framework.mixins.{Locking, TaskParserMixin}
import org.maproulette.models.Task
import play.api.db.Database
import org.slf4j.LoggerFactory

/**
  * For TaskReview
  */
@Singleton
class TaskReviewRepository @Inject() (
    override val db: Database,
    taskRepository: TaskRepository
) extends RepositoryMixin
    with Locking[Task]
    with TaskParserMixin {
  implicit val baseTable: String = TaskReview.TABLE
  protected val logger           = LoggerFactory.getLogger(this.getClass)

  val parser       = this.getTaskParser(this.taskRepository.updateAndRetrieve)
  val reviewParser = this.getTaskWithReviewParser(this.taskRepository.updateAndRetrieve)

  /**
    * Gets a Task object with review data
    *
    * @param taskId
    */
  def getTaskWithReview(taskId: Long): TaskWithReview = {
    this.withMRConnection { implicit c =>
      val query = Query.simple(List())
      query.build(s"""
        SELECT $retrieveColumnsWithReview,
               challenges.name as challenge_name,
               mappers.name as review_requested_by_username,
               reviewers.name as reviewed_by_username
        FROM tasks
        LEFT OUTER JOIN task_review ON task_review.task_id = tasks.id
        LEFT OUTER JOIN users mappers ON task_review.review_requested_by = mappers.id
        LEFT OUTER JOIN users reviewers ON task_review.reviewed_by = reviewers.id
        INNER JOIN challenges ON challenges.id = tasks.parent_id
        WHERE tasks.id = {taskId}
      """).on(Symbol("taskId") -> taskId).as(this.reviewParser.single)
    }
  }

  /**
    * Claims a task for review.
    *
    * @param task Task
    * @param userId User claiming the task
    */
  def claimTaskReview(taskList: List[Task], user: User): Unit = {
    this.withMRTransaction { implicit c =>
      // Unclaim everything before starting a new task.
      Query
        .simple(List())
        .build(
          """UPDATE task_review SET review_claimed_by = NULL, review_claimed_at = NULL
              WHERE review_claimed_by = {userId}"""
        )
        .on(Symbol("userId") -> user.id)
        .executeUpdate()

      for (task <- taskList) {
        Query
          .simple(List())
          .build(
            """UPDATE task_review SET review_claimed_by = {userId}, review_claimed_at = NOW()
                    WHERE task_id = {taskId} AND review_claimed_at IS NULL"""
          )
          .on(Symbol("taskId") -> task.id, Symbol("userId") -> user.id)
          .executeUpdate()

        try {
          this.lockItem(user, task)
        } catch {
          case e: Exception => logger.warn(e.getMessage)
        }

        implicit val id = task.id
        this.taskRepository.cacheManager.withUpdatingCache(this.taskRepository.retrieve) {
          implicit cachedItem =>
            val result =
              Some(task.copy(review = task.review.copy(reviewClaimedBy = Option(user.id.toInt))))
            result
        }(task.id, true, true)
      }
    }
  }

  /**
    * Unclaims task reviews
    *
    * @param task
    * @param user
    */
  def unclaimTaskReview(task: Task, user: User): Unit = {
    this.withMRTransaction { implicit c =>
      val updatedRows =
        Query
          .simple(List())
          .build(
            """UPDATE task_review SET review_claimed_by = NULL, review_claimed_at = NULL
              WHERE task_review.task_id = {taskId}"""
          )
          .on(Symbol("taskId") -> task.id)
          .executeUpdate()

      // if returning 0, then this is because the item is locked by a different user
      if (updatedRows == 0) {
        throw new IllegalAccessException(
          s"Current task [${task.id} is locked by another user, cannot cancel review at this time."
        )
      }

      try {
        this.unlockItem(user, task)
      } catch {
        case e: Exception => logger.warn(e.getMessage)
      }

      val updatedTask = task.copy(review = task.review.copy(reviewClaimedBy = None))
      this.taskRepository.cacheManager.withOptionCaching { () =>
        Some(updatedTask)
      }
    }
  }

  private def buildTaskQuery(query: Query): SimpleSql[Row] = {
    query.build(
      s"""
        SELECT
          ROW_NUMBER() OVER (${query.order.sql()}) as row_num,
          tasks.${this.retrieveColumnsWithReview} FROM tasks
          INNER JOIN challenges c ON c.id = tasks.parent_id
          LEFT OUTER JOIN task_review ON task_review.task_id = tasks.id
          INNER JOIN projects p ON p.id = c.parent_id
       """
    )
  }

  /**
    * Query Tasks
    *
    * @param query
    */
  def queryTasks(query: Query): List[Task] = {
    this.taskRepository.cacheManager.withIDListCaching { implicit cachedItems =>
      this.withMRTransaction { implicit c =>
        this.buildTaskQuery(query).as(this.parser.*)
      }
    }
  }

  /**
    * Query tasks with row number
    *
    * @param query
    */
  def queryTasksWithRowNumber(query: Query): Map[Long, Int] = {
    val rowMap = mutable.Map[Long, Int]()
    val rowNumParser: RowParser[Long] = {
      get[Int]("row_num") ~ get[Long]("tasks.id") map {
        case row ~ id => {
          rowMap.put(id, row)
          id
        }
      }
    }

    this.withMRTransaction { implicit c =>
      this.buildTaskQuery(query).as(rowNumParser.*)
    }

    rowMap.toMap
  }

  /**
    * Get the full count of tasks returned by this query.
    *
    * @param query - Query object. Ordering/Paging are ignored
    */
  def queryTaskCount(query: Query): Int = {
    this.withMRTransaction { implicit c =>
      // Remove ordering and paging when querying task count.
      val simpleQuery = query.copy(order = Order(List()), paging = Paging())
      simpleQuery
        .build(
          s"""SELECT count(*) FROM tasks
            INNER JOIN challenges c ON c.id = tasks.parent_id
            LEFT OUTER JOIN task_review ON task_review.task_id = tasks.id
            INNER JOIN projects p ON p.id = c.parent_id
        """
        )
        .as(SqlParser.int("count").single)
    }
  }

  /**
    * Queries tasks joining on the locked table
    *
    * @param query
    */
  def queryTasksWithLocked(query: Query): List[Task] = {
    this.withMRTransaction { implicit c =>
      query.build(s"""SELECT tasks.$retrieveColumnsWithReview FROM tasks
          LEFT JOIN locked l ON l.item_id = tasks.id
          INNER JOIN challenges c ON c.id = tasks.parent_id
          LEFT OUTER JOIN task_review ON task_review.task_id = tasks.id
          INNER JOIN projects p ON p.id = c.parent_id
        """).as(this.parser.*)
    }
  }

  /**
    * Marks expired taskReviews as unnecessary.
    *
    * @param duration - age of task reviews to treat as 'expired'
    * @return The number of taskReviews that were expired
    */
  def expireTaskReviews(duration: FiniteDuration)(implicit c: Option[Connection] = None): Int = {
    this.withMRTransaction { implicit c =>
      val query = Query.simple(List())

      // Note in task review history we are moving these tasks to unnecessary
      query
        .build(s"""INSERT INTO task_review_history
                          (task_id, requested_by, reviewed_by,
                           review_status, reviewed_at, review_started_at)
                  SELECT tr.task_id, tr.review_requested_by, NULL,
                        ${Task.REVIEW_STATUS_UNNECESSARY}, NOW(), NULL
                  FROM task_review tr
                  WHERE tr.review_status = ${Task.REVIEW_STATUS_REQUESTED}
                    AND tr.task_id IN (
                      SELECT t.id FROM tasks t
                      WHERE AGE(NOW(), t.mapped_on) > {duration}::INTERVAL
                    )
              """)
        .on(
          Symbol("duration") -> ToParameterValue
            .apply[String]
            .apply(String.valueOf(duration))
        )
        .executeUpdate()

      // Update task review status on old task reviews to "unecessary"
      query
        .build(s"""UPDATE task_review tr
                SET review_status = ${Task.REVIEW_STATUS_UNNECESSARY},
                    reviewed_at = NOW(),
                    review_started_at = NULL,
                    review_claimed_at = NULL,
                    review_claimed_by = NULL
                WHERE tr.review_status = ${Task.REVIEW_STATUS_REQUESTED}
                  AND tr.task_id IN (
                    SELECT t.id FROM tasks t
                    WHERE AGE(NOW(), t.mapped_on) > {duration}::INTERVAL
                  )""")
        .on(
          Symbol("duration") -> ToParameterValue
            .apply[String]
            .apply(String.valueOf(duration))
        )
        .executeUpdate()
    }
  }

  /**
    * Updates the task review table.
    */
  def updateTaskReview(
      user: User,
      task: Task,
      reviewStatus: Int,
      updateColumn: String,
      updateWithUser: Long,
      additionalReviewers: Option[List[Long]]
  ): Int = {
    this.withMRTransaction { implicit c =>
      val updatedRows =
        SQL(s"""UPDATE task_review SET review_status = $reviewStatus,
                                 ${updateColumn} = ${updateWithUser},
                                 reviewed_at = NOW(),
                                 review_started_at = task_review.review_claimed_at,
                                 review_claimed_at = NULL,
                                 review_claimed_by = NULL,
                                 additional_reviewers = ${additionalReviewers match {
          case Some(ar) => "ARRAY[" + ar.mkString(",") + "]"
          case None     => "NULL"
        }}
                             WHERE task_review.task_id = (
                                SELECT tasks.id FROM tasks
                                LEFT JOIN locked l on l.item_id = tasks.id AND l.item_type = ${task.itemType.typeId}
                                WHERE tasks.id = ${task.id} AND (l.user_id = ${user.id} OR l.user_id IS NULL)
                              )""").executeUpdate()
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

      updatedRows
    }
  }

  /**
    * Inserts a row into the task review history table.
    */
  def insertTaskReviewHistory(
      task: Task,
      reviewRequestedBy: Long,
      reviewedBy: Long,
      originalReviewer: Option[Long] = None,
      reviewStatus: Int,
      reviewClaimedAt: DateTime
  ): Unit = {
    this.withMRTransaction { implicit c =>
      SQL(s"""INSERT INTO task_review_history
                        (task_id, requested_by, reviewed_by, review_status,
                         reviewed_at, review_started_at, original_reviewer)
            VALUES (${task.id}, ${reviewRequestedBy}, ${reviewedBy},
                    $reviewStatus, NOW(),
                    ${if (reviewClaimedAt != null) s"'${reviewClaimedAt}'"
                      else "NULL"},
                    ${if (originalReviewer == None) "NULL" else originalReviewer})
         """).executeUpdate()
    }
  }
}
