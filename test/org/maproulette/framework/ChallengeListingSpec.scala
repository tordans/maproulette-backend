/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework
import java.util.UUID

import org.maproulette.models.Task
import org.maproulette.framework.model._
import org.maproulette.framework.psql.{Query, Grouping}
import org.maproulette.framework.psql.filter.BaseParameter
import org.maproulette.framework.repository.ChallengeListingRepository
import org.maproulette.framework.service.ChallengeListingService
import org.maproulette.framework.util.{ChallengeListingTag, FrameworkHelper}
import play.api.Application

/**
  * @author krotstan
  */
class ChallengeListingSpec(implicit val application: Application) extends FrameworkHelper {
  val repository: ChallengeListingRepository =
    this.application.injector.instanceOf(classOf[ChallengeListingRepository])
  val service: ChallengeListingService = this.serviceManager.challengeListing
  var randomUser: User                 = null

  "ChallengeListingRepository" should {
    "make a basic query" taggedAs (ChallengeListingTag) in {
      val challenges = this.repository.query(Query.simple(List(), grouping = Grouping("c.id")))
      challenges.size mustEqual 11
    }
  }

  "ChallengeListingService" should {
    "make a basic query" taggedAs (ChallengeListingTag) in {
      val challenges = this.service.query(
        Query.simple(List(), grouping = Grouping("c.id"))
      )
      challenges.size mustEqual 11
    }

    "fetch challenges with reviews" taggedAs (ChallengeListingTag) in {
      val challenges = this.service.withReviewList(1, User.superUser)
      challenges.size mustEqual 1
    }
  }

  override implicit val projectTestName: String = "ChallengeListingSpecProject"

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val createdReviewChallenge = this.challengeDAL
      .insert(
        Challenge(
          -1,
          "reviewChallenge",
          null,
          null,
          general = ChallengeGeneral(
            User.superUser.osmProfile.id,
            this.defaultProject.id,
            "TestChallengeInstruction"
          ),
          creation = ChallengeCreation(),
          priority = ChallengePriority(),
          extra = ChallengeExtra()
        ),
        User.superUser
      )
    val task = this.taskDAL
      .insert(
        this.getTestTask(UUID.randomUUID().toString, createdReviewChallenge.id),
        User.superUser
      )

    randomUser = this.serviceManager.user.create(
      this.getTestUser(12345, "RandomOUser"),
      User.superUser
    )
    this.taskDAL.setTaskStatus(List(task), Task.STATUS_FIXED, randomUser, Some(true))
  }
}
