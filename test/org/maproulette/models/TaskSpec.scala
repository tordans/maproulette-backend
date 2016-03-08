package org.maproulette.models

import org.junit.runner.RunWith
import org.maproulette.models.dal.{TaskDAL, ProjectDAL, ChallengeDAL}
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import play.api.libs.json.Json
import play.api.test.WithApplication

/**
  * @author cuthbertm
  */
@RunWith(classOf[JUnitRunner])
class TaskSpec extends Specification {
  implicit var taskID:Long = -1

  sequential

  "Tasks" should {
    "write tasks object to database" in new WithApplication {
      val projectID = ProjectDAL.insert(Project(-1, "RootProject_tasktest")).id
      val challengeID = ChallengeDAL.insert(Challenge(-1, "ChallengeProject", None, projectID)).id
      val newTask = Task(-1, "NewTask", None, challengeID, "Instructions for task", Json.parse("""{"type":"Point","coordinates":[77.6255107,40.5872232]}"""))
      taskID = TaskDAL.insert(newTask).id
      TaskDAL.retrieveById match {
        case Some(t) =>
          t.name mustEqual newTask.name
          t.instruction mustEqual newTask.instruction
        case None =>
          // fail here automatically because we should have retrieved the tag
          1 mustEqual 2
      }
    }

    "update tasks object to database" in new WithApplication {
      TaskDAL.update(Json.parse(
        """{
          "name":"UpdatedTask"
        }""".stripMargin))(taskID)
      TaskDAL.retrieveById match {
        case Some(t) =>
          t.name mustEqual "UpdatedTask"
          t.id mustEqual taskID
        case None =>
          // fail here automatically because we should have retrieved the tag
          1 mustEqual 2
      }
    }

    "delete tasks object in database" in new WithApplication {
      implicit val ids = List(taskID)
      TaskDAL.delete
      TaskDAL.retrieveById mustEqual None
    }
  }
}
