package org.maproulette.models

import org.junit.runner.RunWith
import org.maproulette.models.dal.{ProjectDAL, ChallengeDAL}
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import play.api.libs.json.Json
import play.api.test.WithApplication

/**
  * @author cuthbertm
  */
@RunWith(classOf[JUnitRunner])
class ChallengeSpec extends Specification {
  implicit var challengeID:Long = -1

  sequential

  "Challenges" should {
    "write challenge object to database" in new WithApplication {
      val projectID = ProjectDAL.insert(Project(-1, "RootProject_challengeTest")).id
      val newChallenge = Challenge(challengeID, "NewProject", None, projectID, None, Some("This is a newProject"))
      challengeID = ChallengeDAL.insert(newChallenge).id
      ChallengeDAL.retrieveById match {
        case Some(t) =>
          t.name mustEqual newChallenge.name
          t.description mustEqual newChallenge.description
        case None =>
          // fail here automatically because we should have retrieved the tag
          1 mustEqual 2
      }
    }

    "update challenge object to database" in new WithApplication {
      ChallengeDAL.update(Json.parse(
        """{
          "name":"UpdatedChallenge"
        }""".stripMargin))(challengeID)
      ChallengeDAL.retrieveById match {
        case Some(t) =>
          t.name mustEqual "UpdatedChallenge"
          t.id mustEqual challengeID
        case None =>
          // fail here automatically because we should have retrieved the tag
          1 mustEqual 2
      }
    }

    "delete challenge object in database" in new WithApplication {
      implicit val ids = List(challengeID)
      ChallengeDAL.deleteFromIdList
      ChallengeDAL.retrieveById mustEqual None
    }
  }
}
