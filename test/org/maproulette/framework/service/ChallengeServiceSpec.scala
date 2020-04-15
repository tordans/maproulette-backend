/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.service

import org.maproulette.framework.model.Challenge
import org.maproulette.framework.psql.Query
import org.maproulette.framework.psql.filter.BaseParameter
import org.maproulette.framework.util.{ChallengeTag, FrameworkHelper}
import play.api.Application

/**
  * @author mcuthbert
  */
class ChallengeServiceSpec(implicit val application: Application) extends FrameworkHelper {
  val service: ChallengeService = this.serviceManager.challenge

  "ChallengeService" should {
    "make a basic query" taggedAs ChallengeTag in {
      val challenges = this.service.query(
        Query.simple(List(BaseParameter(Challenge.FIELD_ID, this.defaultChallenge.id)))
      )
      challenges.size mustEqual 1
      challenges.head.id mustEqual this.defaultChallenge.id
    }
  }

  override implicit val projectTestName: String = "ChallengeServiceSpecProject"
}
