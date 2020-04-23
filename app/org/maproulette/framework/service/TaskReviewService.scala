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
import org.maproulette.models.Task

/**
  * Service layer for TaskReview
  *
  * @author krotstan
  */
@Singleton
class TaskReviewService @Inject() (repository: TaskReviewRepository) {

  /**
    * Marks expired taskReviews as unnecessary.
    *
    * @param duration - age of task reviews to treat as 'expired'
    * @return The number of taskReviews that were expired
    */
  def expireTaskReviews(duration: FiniteDuration): Int = {
    this.repository.expireTaskReviews(duration)
  }
}
