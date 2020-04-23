/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.service

import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

import org.maproulette.framework.model._
import org.maproulette.framework.psql.{GroupField, Grouping, Query}
import org.maproulette.framework.util.{TaskReviewTag, FrameworkHelper}
import org.maproulette.models.Task
import play.api.Application

/**
  * @author krotstan
  */
class TaskReviewServiceSpec(implicit val application: Application) extends FrameworkHelper {
  val service: TaskReviewService = this.serviceManager.taskReview

  "TaskReviewService" should {
    "expire old task reviews" taggedAs (TaskReviewTag) in {
      val expiredTaskReviews =
        this.service.expireTaskReviews(FiniteDuration(1000, TimeUnit.MILLISECONDS))
      expiredTaskReviews mustEqual 0
    }
  }

  override implicit val projectTestName: String = "TaskReviewSpecProject"
}
