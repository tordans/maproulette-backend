package org.maproulette.models

import javax.inject.Inject

import org.joda.time.DateTime
import org.junit.runner.RunWith
import org.maproulette.models.dal.{ProjectDAL, SurveyDAL}
import org.maproulette.session.User
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import play.api.libs.json.Json
import play.api.test.WithApplication

/**
  * @author cuthbertm
  */
@RunWith(classOf[JUnitRunner])
class SurveySpec @Inject() (projectDAL: ProjectDAL, surveyDAL: SurveyDAL) extends Specification {
  implicit var surveyID:Long = -1

  sequential

  "Surveys" should {
    "write survey object to database" in new WithApplication {
      val projectID = projectDAL.insert(Project(-1, "RootProject_challengeTest", DateTime.now(), DateTime.now()), User.superUser).id
      val answers = List(Answer(-1, "Answer1"), Answer(-1, "Answer2"))
      val newSurvey = Survey(Challenge(surveyID, "newSurvey", DateTime.now(), DateTime.now(), DateTime.now(), Some("This is a new survey"),
        ChallengeGeneral(-1, projectID, "Default Question"),
        ChallengeCreation(),
        ChallengePriority(),
        ChallengeExtra()
      ), answers)
      surveyID = surveyDAL.insert(newSurvey, User.superUser).challenge.id
      surveyDAL.retrieveById match {
        case Some(t) =>
          t.name mustEqual newSurvey.name
          t.description mustEqual newSurvey.description
          t.question mustEqual newSurvey.question
        case None =>
          // fail here automatically because we should have retrieved the tag
          1 mustEqual 2
      }
    }

    "update survey object to database" in new WithApplication {
      surveyDAL.update(Json.parse(
        """{
          "name":"UpdatedSurvey"
        }""".stripMargin), User.superUser)(surveyID)
      surveyDAL.retrieveById match {
        case Some(t) =>
          t.name mustEqual "UpdatedSurvey"
          t.id mustEqual surveyID
        case None =>
          // fail here automatically because we should have retrieved the tag
          1 mustEqual 2
      }
    }

    "delete challenge object in database" in new WithApplication {
      surveyDAL.delete(surveyID, User.superUser)
      surveyDAL.retrieveById mustEqual None
    }
  }
}

