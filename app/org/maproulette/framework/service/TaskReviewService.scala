/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.service

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration

import org.maproulette.framework.model._
import org.maproulette.framework.psql.{Query, _}
import org.maproulette.framework.psql.filter.{BaseParameter, _}
import org.maproulette.framework.repository.TaskReviewRepository
import org.maproulette.session.SearchParameters

import org.maproulette.models.Task
import org.maproulette.models.dal.TaskReviewDAL

/**
  * Service layer for TaskReview
  *
  * @author krotstan
  */
@Singleton
class TaskReviewService @Inject() (repository: TaskReviewRepository, taskReviewDAL: TaskReviewDAL) {

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
    * Gets a list of tasks that have been reviewed (either by this user or requested by this user)
    *
    * @param user      The user executing the request
    * @param reviewTasksType
    * @param searchParameters
    * @param startDate Limit tasks to reviewed after date (YYYY-MM-DD)
    * @param endDate   Limit tasks to reviewed before date (YYYY-MM-DD)
    * @return A list of tasks
    */
  def getReviewMetrics(
      user: User,
      reviewTasksType: Int,
      searchParameters: SearchParameters,
      mappers: Option[List[String]] = None,
      reviewers: Option[List[String]] = None,
      priorities: Option[List[Int]] = None,
      startDate: String,
      endDate: String,
      onlySaved: Boolean = false,
      excludeOtherReviewers: Boolean = false
  ): ReviewMetrics = {
    this.taskReviewDAL.getReviewMetrics(
      user,
      reviewTasksType,
      searchParameters,
      mappers,
      reviewers,
      priorities,
      startDate,
      endDate,
      onlySaved,
      excludeOtherReviewers
    )
  }

}
