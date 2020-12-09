/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package org.maproulette.framework.mixins

import play.api.libs.json._
import anorm.SqlParser.get
import anorm.{RowParser, ~}
import org.joda.time.DateTime

import org.maproulette.framework.service.ServiceManager
import org.maproulette.framework.model.{TaskReview, TaskWithReview, User}

import org.maproulette.utils.Utils
import org.maproulette.models.Task
import org.maproulette.models.{TaskReviewFields}

/**
  * TaskParserMixin provides task parsers
  */
trait TaskParserMixin {
  // The columns to be retrieved for the task. Reason this is required is because one of the columns
  // "tasks.location" is a PostGIS object in the database and we want it returned in GeoJSON instead
  // so the ST_AsGeoJSON function is used to convert it to geoJSON
  val retrieveColumnsWithReview: String =
    "*, tasks.geojson::TEXT AS geo_json, " +
      "tasks.cooperative_work_json::TEXT AS cooperative_work, tasks.completion_responses::TEXT AS responses, " +
      "ST_AsGeoJSON(tasks.location) AS geo_location " +
      ", task_review.review_status, task_review.review_requested_by, " +
      "task_review.reviewed_by, task_review.reviewed_at, task_review.review_started_at, " +
      "task_review.review_claimed_by, task_review.review_claimed_at, task_review.additional_reviewers "

  // The anorm row parser to convert records from the task table to task objects
  def getTaskParser(
      updateAndRetrieve: (Long, Option[String], Option[String], Option[String]) => (
          String,
          Option[String],
          Option[String]
      )
  ): RowParser[Task] = {
    get[Long]("tasks.id") ~
      get[String]("tasks.name") ~
      get[DateTime]("tasks.created") ~
      get[DateTime]("tasks.modified") ~
      get[Long]("tasks.parent_id") ~
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
      get[Option[String]]("responses") ~
      get[Option[Long]]("tasks.bundle_id") ~
      get[Option[Boolean]]("tasks.is_bundle_primary") map {
      case id ~ name ~ created ~ modified ~ parent_id ~ instruction ~ location ~ status ~ geojson ~
            cooperativeWork ~ mappedOn ~ completedTimeSpent ~ completedBy ~ reviewStatus ~
            reviewRequestedBy ~ reviewedBy ~ reviewedAt ~ reviewStartedAt ~ reviewClaimedBy ~
            reviewClaimedAt ~ additionalReviewers ~ priority ~ changesetId ~ responses ~ bundleId ~ isBundlePrimary =>
        val values = updateAndRetrieve(id, geojson, location, cooperativeWork)
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
        )
    }
  }

  def getTaskWithReviewParser(
      updateAndRetrieve: (Long, Option[String], Option[String], Option[String]) => (
          String,
          Option[String],
          Option[String]
      )
  ): RowParser[TaskWithReview] = {
    get[Long]("tasks.id") ~
      get[String]("tasks.name") ~
      get[DateTime]("tasks.created") ~
      get[DateTime]("tasks.modified") ~
      get[Long]("tasks.parent_id") ~
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
        val values = updateAndRetrieve(id, geojson, location, cooperativeWork)
        TaskWithReview(
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
}
