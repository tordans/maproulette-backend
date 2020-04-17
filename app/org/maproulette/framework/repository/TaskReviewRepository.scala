/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.repository

import java.sql.Connection
import scala.concurrent.duration.FiniteDuration

import anorm.SqlParser.get
import anorm.ToParameterValue
import anorm.{RowParser, ~}
import javax.inject.{Inject, Singleton}
import org.maproulette.framework.model.TaskReview
import org.maproulette.framework.psql.Query
import org.maproulette.models.Task
import play.api.db.Database

/**
  * @author mcuthbert
  */
@Singleton
class TaskReviewRepository @Inject() (override val db: Database) extends RepositoryMixin {
  implicit val baseTable: String = TaskReview.TABLE

  def getTaskReviewCounts(query: Query)(implicit c: Option[Connection] = None): Map[String, Int] = {
    this.withMRTransaction { implicit c =>
      val reviewCountsParser: RowParser[Map[String, Int]] = {
        get[Int]("total") ~
          get[Int]("approvedCount") ~
          get[Int]("rejectedCount") ~
          get[Int]("assistedCount") ~
          get[Int]("disputedCount") ~
          get[Int]("requestedCount") ~
          get[Double]("total_review_time") ~
          get[Int]("tasks_with_review_time") map {
          case total ~ approvedCount ~ rejectedCount ~ assistedCount ~ disputedCount ~
                requestedCount ~ totalReviewTime ~ tasksWithReviewTime => {
            Map(
              "total"     -> total,
              "approved"  -> approvedCount,
              "rejected"  -> rejectedCount,
              "assisted"  -> assistedCount,
              "disputed"  -> disputedCount,
              "requested" -> requestedCount,
              "avgReviewTime" -> (if (tasksWithReviewTime > 0)
                                    (totalReviewTime / tasksWithReviewTime).toInt
                                  else 0)
            )
          }
        }
      }

      query.build(s"""
                       |SELECT count(*) as total,
                       |COALESCE(sum(case when review_status = ${Task.REVIEW_STATUS_APPROVED} then 1 else 0 end), 0) approvedCount,
                       |COALESCE(sum(case when review_status = ${Task.REVIEW_STATUS_REJECTED} then 1 else 0 end), 0) rejectedCount,
                       |COALESCE(sum(case when review_status = ${Task.REVIEW_STATUS_ASSISTED} then 1 else 0 end), 0) assistedCount,
                       |COALESCE(sum(case when review_status = ${Task.REVIEW_STATUS_DISPUTED} then 1 else 0 end), 0) disputedCount,
                       |COALESCE(sum(case when review_status = ${Task.REVIEW_STATUS_REQUESTED} then 1 else 0 end), 0) requestedCount,
                       |COALESCE(SUM(CASE WHEN (review_started_at IS NOT NULL AND
                       |                        reviewed_at IS NOT NULL)
                       |                  THEN (EXTRACT(EPOCH FROM (reviewed_at - review_started_at)) * 1000)
                       |                  ELSE 0 END), 0) total_review_time,
                       |COALESCE(SUM(CASE WHEN (review_started_at IS NOT NULL AND
                       |                        reviewed_at IS NOT NULL)
                       |                  THEN 1 ELSE 0 END), 0) tasks_with_review_time
                       |FROM task_review
        """.stripMargin).as(reviewCountsParser.single)
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
                      WHERE AGE(NOW(), t.modified) > {duration}::INTERVAL
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
                    WHERE AGE(NOW(), t.modified) > {duration}::INTERVAL
                  )""")
        .on(
          Symbol("duration") -> ToParameterValue
            .apply[String]
            .apply(String.valueOf(duration))
        )
        .executeUpdate()
    }
  }
}
