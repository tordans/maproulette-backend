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
import org.maproulette.framework.model.{TaskReview, ReviewMetrics}
import org.maproulette.framework.psql.{Query, Grouping, GroupField}
import org.maproulette.models.Task
import play.api.db.Database

/**
  * @author mcuthbert
  */
@Singleton
class TaskReviewRepository @Inject() (override val db: Database) extends RepositoryMixin {
  implicit val baseTable: String = TaskReview.TABLE

  val reviewMetricsParser: RowParser[ReviewMetrics] = {
    get[Int]("total") ~
      get[Int]("requested") ~
      get[Int]("approved") ~
      get[Int]("rejected") ~
      get[Int]("assisted") ~
      get[Int]("disputed") ~
      get[Int]("fixed") ~
      get[Int]("falsePositive") ~
      get[Int]("skipped") ~
      get[Int]("alreadyFixed") ~
      get[Int]("tooHard") ~
      get[Double]("totalReviewTime") ~
      get[Int]("tasksWithReviewTime") ~
      get[Long]("user_id").? ~
      get[String]("tag_name").? map {
      case total ~ requested ~ approved ~ rejected ~ assisted ~ disputed ~
            fixed ~ falsePositive ~ skipped ~ alreadyFixed ~ tooHard ~
            totalReviewTime ~ tasksWithReviewTime ~ userId ~ tagName => {
        new ReviewMetrics(
          total,
          requested,
          approved,
          rejected,
          assisted,
          disputed,
          fixed,
          falsePositive,
          skipped,
          alreadyFixed,
          tooHard,
          if (tasksWithReviewTime > 0) (totalReviewTime / tasksWithReviewTime) else 0,
          userId,
          tagName
        )
      }
    }
  }

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

  def executeReviewMetricsQuery(
      query: Query,
      joinClause: StringBuilder = new StringBuilder(),
      groupByMappers: Boolean = false,
      groupByTags: Boolean = false
  ): List[ReviewMetrics] = {
    var groupFields = ""
    var groupBy =
      if (groupByMappers) {
        groupFields = ", review_requested_by as user_id"
        Grouping(GroupField(TaskReview.FIELD_REVIEW_REQUESTED_BY))
      } else if (groupByTags) {
        groupFields = ", tags.name as tag_name"
        joinClause ++= "INNER JOIN tags_on_tasks tot ON tot.task_id = tasks.id "
        joinClause ++= "INNER JOIN tags tags ON tags.id = tot.tag_id "
        Grouping(GroupField("name", table = Some("tags")))
      } else {
        Grouping()
      }

    this.withMRTransaction { implicit c =>
      query
        .build(
          s"""
         SELECT COUNT(*) AS total,
         COUNT(tasks.completed_time_spent) as tasksWithReviewTime,
         COALESCE(SUM(EXTRACT(EPOCH FROM (reviewed_at - review_started_at)) * 1000),0) as totalReviewTime,
         COUNT(review_status) FILTER (where review_status = 0) AS requested,
         COUNT(review_status) FILTER (where review_status = 1) AS approved,
         COUNT(review_status) FILTER (where review_status = 2) AS rejected,
         COUNT(review_status) FILTER (where review_status = 3) AS assisted,
         COUNT(review_status) FILTER (where review_status = 4) AS disputed,
         COUNT(tasks.status) FILTER (where tasks.status = 1) AS fixed,
         COUNT(tasks.status) FILTER (where tasks.status = 2) AS falsePositive,
         COUNT(tasks.status) FILTER (where tasks.status = 3) AS skipped,
         COUNT(tasks.status) FILTER (where tasks.status = 5) AS alreadyFixed,
         COUNT(tasks.status) FILTER (where tasks.status = 6) AS tooHard
         ${groupFields}
         FROM tasks
         INNER JOIN challenges c ON c.id = tasks.parent_id
         LEFT OUTER JOIN task_review ON task_review.task_id = tasks.id
         INNER JOIN projects p ON p.id = c.parent_id
         ${joinClause}
        """,
          groupBy
        )
        .as(reviewMetricsParser.*)
    }
  }
}
