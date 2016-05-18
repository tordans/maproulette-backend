package org.maproulette.models

import javax.inject.Inject

import org.junit.runner.RunWith
import org.maproulette.models.dal.{ProjectDAL, ChallengeDAL}
import org.maproulette.session.User
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import play.api.libs.json.Json
import play.api.test.WithApplication

/**
  * @author cuthbertm
  */
@RunWith(classOf[JUnitRunner])
class ChallengeSpec @Inject() (projectDAL: ProjectDAL, challengeDAL: ChallengeDAL) extends Specification {
  implicit var challengeID:Long = -1

  sequential

  "Challenges" should {
    "write challenge object to database" in new WithApplication {
      val projectID = projectDAL.insert(Project(-1, "RootProject_challengeTest"), User.superUser).id
      val newChallenge = Challenge(challengeID, "NewChallenge", Some("This is a new challenge"), projectID, "")
      challengeID = challengeDAL.insert(newChallenge, User.superUser).id
      challengeDAL.retrieveById match {
        case Some(t) =>
          t.name mustEqual newChallenge.name
          t.description mustEqual newChallenge.description
        case None =>
          // fail here automatically because we should have retrieved the tag
          1 mustEqual 2
      }
    }

    "update challenge object to database" in new WithApplication {
      challengeDAL.update(Json.parse(
        """{
          "name":"UpdatedChallenge"
        }""".stripMargin), User.superUser)(challengeID)
      challengeDAL.retrieveById match {
        case Some(t) =>
          t.name mustEqual "UpdatedChallenge"
          t.id mustEqual challengeID
        case None =>
          // fail here automatically because we should have retrieved the tag
          1 mustEqual 2
      }
    }

    "delete challenge object in database" in new WithApplication {
      challengeDAL.delete(challengeID, User.superUser)
      challengeDAL.retrieveById mustEqual None
    }
  }
}
