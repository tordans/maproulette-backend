package org.maproulette.framework

import org.maproulette.framework.model.Challenge
import org.maproulette.framework.psql.Query
import org.maproulette.framework.psql.filter.BaseParameter
import org.maproulette.framework.repository.ChallengeRepository
import org.maproulette.framework.service.ChallengeService
import org.maproulette.utils.TestDatabase

/**
  * @author mcuthbert
  */
class ChallengeSpec extends TestDatabase {
  val repository: ChallengeRepository =
    this.application.injector.instanceOf(classOf[ChallengeRepository])
  val service: ChallengeService = this.serviceManager.challenge

  "ChallengeRepository" should {
    "make a basic query" in {
      val challenges = this.repository.query(
        Query.simple(List(BaseParameter(Challenge.FIELD_ID, this.defaultChallenge.id)))
      )
      challenges.size mustEqual 1
      challenges.head.id mustEqual this.defaultChallenge.id
    }
  }

  "ChallengeService" should {
    "make a basic query" in {
      val challenges = this.service.query(
        Query.simple(List(BaseParameter(Challenge.FIELD_ID, this.defaultChallenge.id)))
      )
      challenges.size mustEqual 1
      challenges.head.id mustEqual this.defaultChallenge.id
    }
  }
}
