package org.maproulette.models

import javax.inject.Inject

import org.junit.runner.RunWith
import org.maproulette.models.dal.{TaskDAL, ProjectDAL, ChallengeDAL}
import org.maproulette.session.User
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import play.api.libs.json.Json
import play.api.test.WithApplication

/**
  * @author cuthbertm
  */
@RunWith(classOf[JUnitRunner])
class TaskSpec @Inject() (projectDAL: ProjectDAL, challengeDAL: ChallengeDAL, taskDAL: TaskDAL) extends Specification {
  implicit var taskID:Long = -1

  sequential

  "Tasks" should {
    "write tasks object to database" in new WithApplication {
      val projectID = projectDAL.insert(Project(-1, "RootProject_tasktest"), User.superUser).id
      val challengeID = challengeDAL.insert(Challenge(-1, "ChallengeProject", None, None, projectID), User.superUser).id
      val newTask = Task(-1, "NewTask", None, challengeID, "Instructions for task", Json.parse("""{"type":"Point","coordinates":[77.6255107,40.5872232]}"""))
      taskID = taskDAL.insert(newTask, User.superUser).id
      taskDAL.retrieveById match {
        case Some(t) =>
          t.name mustEqual newTask.name
          t.instruction mustEqual newTask.instruction
        case None =>
          // fail here automatically because we should have retrieved the tag
          1 mustEqual 2
      }
    }

    "update tasks object to database" in new WithApplication {
      taskDAL.update(Json.parse(
        """{
          "name":"UpdatedTask"
        }""".stripMargin), User.superUser)(taskID)
      taskDAL.retrieveById match {
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
      taskDAL.deleteFromIdList(User.superUser)
      taskDAL.retrieveById mustEqual None
    }
  }
}
