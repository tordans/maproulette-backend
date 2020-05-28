/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.service

import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import play.api.libs.json._

import org.maproulette.framework.model._
import org.maproulette.framework.psql.Query
import org.maproulette.framework.util.{TaskTag, FrameworkHelper}
import org.maproulette.models.Task
import org.maproulette.exception.NotFoundException
import play.api.Application

/**
  * @author krotstan
  */
class TaskServiceSpec(implicit val application: Application) extends FrameworkHelper {
  val service: TaskService = this.serviceManager.task

  "TaskService" should {
    "update completion responses" taggedAs (TaskTag) in {
      val testResponse = "{\"a\":1}"
      this.service.updateCompletionResponses(defaultTask.id, defaultUser, Json.parse(testResponse))
      val task = this.service.retrieve(defaultTask.id)
      task.get.completionResponses.get.toString mustEqual testResponse
    }

    "update completion responses again ok" taggedAs (TaskTag) in {
      val testResponse = "{\"a\":2,\"b\":\"yes\"}"
      this.service.updateCompletionResponses(defaultTask.id, defaultUser, Json.parse(testResponse))
      val task = this.service.retrieve(defaultTask.id)
      task.get.completionResponses.get.toString mustEqual testResponse
    }

    "Fail if task not found when update completion responses" taggedAs (TaskTag) in {
      val testResponse = "{\"a\":1}"
      intercept[NotFoundException] {
        this.service.updateCompletionResponses(-1010, defaultUser, Json.parse(testResponse))
      }
    }
  }

  override implicit val projectTestName: String = "TaskSpecProject"
}
