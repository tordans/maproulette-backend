/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.service

import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import play.api.libs.json.Json

import org.maproulette.session.{
  SearchParameters,
  SearchChallengeParameters,
  SearchReviewParameters,
  SearchTaskParameters
}
import org.maproulette.framework.model._
import org.maproulette.framework.psql.{GroupField, Grouping, Query}
import org.maproulette.framework.util.{TaskReviewTag, FrameworkHelper}
import org.maproulette.models.Task
import org.maproulette.models.dal.{ChallengeDAL, TaskDAL}
import play.api.Application

/**
  * @author krotstan
  */
class TaskReviewServiceSpec(implicit val application: Application) extends FrameworkHelper {
  val service: TaskReviewService = this.serviceManager.taskReview
  var randomChallenge: Challenge = null
  var randomUser: User           = null
  var reviewUser: User           = null
  var reviewTask: Task           = null

  "TaskReviewService" should {
    "expire old task reviews" taggedAs (TaskReviewTag) in {
      val expiredTaskReviews =
        this.service.expireTaskReviews(FiniteDuration(1000, TimeUnit.MILLISECONDS))
      expiredTaskReviews mustEqual 0
    }

    "start/cancel a task review" taggedAs (TaskReviewTag) in {
      val result = this.service.startTaskReview(randomUser, reviewTask)
      result.get.review.reviewClaimedBy.get mustEqual randomUser.id

      val result2 = this.service.cancelTaskReview(randomUser, result.get)
      result2.get.review.reviewClaimedBy mustEqual None
    }

    "next task review should return None for non-reviewer" taggedAs (TaskReviewTag) in {
      // Only users who are reviewers can see review tasks
      var noResult =
        this.service.nextTaskReview(randomUser, SearchParameters(), sort = "", order = "")
      noResult mustEqual None
    }

    "next task review" taggedAs (TaskReviewTag) in {
      // Get a task to review
      var result =
        this.service.nextTaskReview(reviewUser, SearchParameters(), sort = "id", order = "")
      result = this.service.startTaskReview(reviewUser, result.get)
      result.get.review.reviewClaimedBy.get mustEqual reviewUser.id
      result.get.id mustEqual reviewTask.id
      this.service.cancelTaskReview(reviewUser, result.get)

      // Get a second task to review
      var result2 = this.service.nextTaskReview(
        reviewUser,
        SearchParameters(),
        lastTaskId = Some(result.get.id),
        sort = "id",
        order = ""
      )
      result2 = this.service.startTaskReview(reviewUser, result2.get)
      result2.get.review.reviewClaimedBy.get mustEqual reviewUser.id

      // Next task must not be the same as the first
      (result2.get.id == result.get.id) mustEqual false
      this.service.cancelTaskReview(reviewUser, result2.get)

      // Only 2 tasks so next next task should be None
      var result3 = this.service.nextTaskReview(
        reviewUser,
        SearchParameters(),
        lastTaskId = Some(result2.get.id),
        sort = "id",
        order = ""
      )
      result3 mustEqual None
    }

    "next task review should honor sort direction" taggedAs (TaskReviewTag) in {
      // Get a task to review
      var result =
        this.service.nextTaskReview(reviewUser, SearchParameters(), sort = "id", order = "DESC")
      result = this.service.startTaskReview(reviewUser, result.get)
      result.get.review.reviewClaimedBy.get mustEqual reviewUser.id
      this.service.cancelTaskReview(reviewUser, result.get)

      // Get a second task to review
      var result2 = this.service.nextTaskReview(
        reviewUser,
        SearchParameters(),
        lastTaskId = Some(result.get.id),
        sort = "id",
        order = "DESC"
      )
      result2 = this.service.startTaskReview(reviewUser, result2.get)
      result2.get.review.reviewClaimedBy.get mustEqual reviewUser.id
      result2.get.id mustEqual reviewTask.id
      this.service.cancelTaskReview(reviewUser, result2.get)

      // Exercise sorting
      var result3 = this.service.nextTaskReview(
        reviewUser,
        SearchParameters(),
        sort = "review_requested_by",
        order = "ASC"
      )
      (result3 != None) mustEqual true
      var result4 = this.service.nextTaskReview(
        reviewUser,
        SearchParameters(),
        sort = "mapped_on",
        order = "ASC"
      )
      (result4 != None) mustEqual true
    }

    "get Review Requested tasks" taggedAs (TaskReviewTag) in {
      var (count, result) =
        this.service.getReviewRequestedTasks(reviewUser, SearchParameters(), sort = "", order = "")
      count mustEqual 2
      result.length mustEqual 2
    }

    "get Nearby Review Tasks" taggedAs (TaskReviewTag) in {
      val results = this.service.getNearbyReviewTasks(reviewUser, SearchParameters(), reviewTask.id)
      results.length mustEqual 1
      (results.head.id == reviewTask.id) mustEqual false
    }

    "get Nearby Review Tasks does not returned claimed tasks" taggedAs (TaskReviewTag) in {
      // Get a task (other than reviewTask) and start reviewing it
      var task = this.service.nextTaskReview(
        reviewUser,
        SearchParameters(),
        lastTaskId = Some(reviewTask.id),
        sort = "id",
        order = "ASC"
      )
      task = this.service.startTaskReview(reviewUser, task.get)

      val results =
        this.service.getNearbyReviewTasks(User.superUser, SearchParameters(), reviewTask.id)
      results.length mustEqual 0
    }

    "get Reviewed tasks" taggedAs (TaskReviewTag) in {
      // Allow review needed
      var (count, result) =
        this.service.getReviewedTasks(reviewUser, SearchParameters(), true, sort = "", order = "")
      count mustEqual 3
      result.length mustEqual 3

      var (count2, result2) = this.service.getReviewedTasks(
        reviewUser,
        SearchParameters(),
        false,
        10,
        0,
        sort = "reviewed_by",
        order = "DESC"
      )
      count2 mustEqual 1
      result2.length mustEqual 1
    }

    "get Review Task Clusters" taggedAs (TaskReviewTag) in {
      val result = this.service.getReviewTaskClusters(
        User.superUser,
        this.service.REVIEW_REQUESTED_TASKS,
        SearchParameters(),
        10
      )
      result.head.numberOfPoints mustEqual 2

      val result2 = this.service.getReviewTaskClusters(
        User.superUser,
        this.service.MY_REVIEWED_TASKS,
        SearchParameters(),
        10
      )
      result2.head.numberOfPoints mustEqual 1

      val result3 = this.service.getReviewTaskClusters(
        User.superUser,
        -1,
        SearchParameters(
          taskParams = SearchTaskParameters(taskReviewStatus = Some(List(0, 1, 2, 3, 4)))
        ),
        10
      )
      result3.head.numberOfPoints mustEqual 3
    }
  }

  override implicit val projectTestName: String = "TaskReviewSpecProject"

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val (c, u, u2, t) =
      TaskReviewServiceSpec.setup(
        this.challengeDAL,
        this.taskDAL,
        this.serviceManager,
        this.defaultProject.id,
        this.getTestTask,
        this.getTestUser
      )
    randomChallenge = c
    randomUser = u
    reviewUser = u2
    reviewTask = t
  }
}

object TaskReviewServiceSpec {
  def setup(
      challengeDAL: ChallengeDAL,
      taskDAL: TaskDAL,
      serviceManager: ServiceManager,
      projectId: Long,
      taskFunc: (String, Long) => Task,
      userFunc: (Long, String) => User
  ): (Challenge, User, User, Task) = {
    // Project and Challenge must be enabled for a normal reviewer to find a review task
    serviceManager.project.update(projectId, Json.obj("enabled" -> true), User.superUser)
    val createdReviewChallenge = challengeDAL
      .insert(
        Challenge(
          -1,
          "reviewChallenge",
          null,
          null,
          general = ChallengeGeneral(
            User.superUser.osmProfile.id,
            projectId,
            "TestChallengeInstruction",
            enabled = true
          ),
          creation = ChallengeCreation(),
          priority = ChallengePriority(),
          extra = ChallengeExtra()
        ),
        User.superUser
      )

    val task = taskDAL
      .insert(
        taskFunc(UUID.randomUUID().toString, createdReviewChallenge.id),
        User.superUser
      )

    val createdReviewChallenge2 = challengeDAL
      .insert(
        Challenge(
          -1,
          "reviewChallenge2",
          null,
          null,
          general = ChallengeGeneral(
            User.superUser.osmProfile.id,
            projectId,
            "TestChallengeInstruction",
            enabled = true
          ),
          creation = ChallengeCreation(),
          priority = ChallengePriority(),
          extra = ChallengeExtra()
        ),
        User.superUser
      )
    var task2 = taskDAL
      .insert(
        taskFunc(UUID.randomUUID().toString, createdReviewChallenge2.id),
        User.superUser
      )

    var newTag = serviceManager.tag.create(
      Tag(id = -1, tagType = "tasks", name = "testTag"),
      User.superUser
    )
    taskDAL.updateItemTags(task2.id, List(newTag.id), User.superUser, true)

    var randomUser = serviceManager.user.create(
      userFunc(12345, "RandomOUser"),
      User.superUser
    )

    // Setup a user who is a reviewer
    var reviewUser = serviceManager.user.create(
      userFunc(67892, "ReviewerUser"),
      User.superUser
    )
    reviewUser = serviceManager.user
      .managedUpdate(
        reviewUser.id,
        reviewUser.settings.copy(isReviewer = Some(true)),
        None,
        reviewUser
      )
      .get

    var task3 = taskDAL
      .insert(
        taskFunc(UUID.randomUUID().toString, createdReviewChallenge.id),
        User.superUser
      )

    taskDAL.setTaskStatus(List(task), Task.STATUS_FIXED, randomUser, Some(true))
    taskDAL.setTaskStatus(List(task2), Task.STATUS_FIXED, User.superUser, Some(true))
    taskDAL.setTaskStatus(List(task3), Task.STATUS_FIXED, User.superUser, Some(true))

    task2 = taskDAL.retrieveById(task2.id).get
    serviceManager.taskReview.setTaskReviewStatus(
      task2,
      Task.REVIEW_STATUS_APPROVED,
      User.superUser,
      None
    )

    (createdReviewChallenge, randomUser, reviewUser, task)
  }
}
