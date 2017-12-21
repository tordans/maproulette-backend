package org.maproulette.models

import javax.inject.Inject

import org.joda.time.DateTime
import org.junit.runner.RunWith
import org.maproulette.models.dal.ProjectDAL
import org.maproulette.session.User
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import play.api.libs.json.Json
import play.api.test.WithApplication

/**
  * @author cuthbertm
  */
@RunWith(classOf[JUnitRunner])
class ProjectSpec @Inject() (projectDAL: ProjectDAL) extends Specification {
  implicit var projectID:Long = -1

  sequential

  "Projects" should {
    "write project object to database" in new WithApplication {
      val newProject = Project(projectID, User.DEFAULT_SUPER_USER_ID, "NewProject_projecttest", DateTime.now(), DateTime.now(), Some("This is a newProject"))
      projectID = projectDAL.insert(newProject, User.superUser).id
      projectDAL.retrieveById match {
        case Some(t) =>
          t.name mustEqual newProject.name
          t.description mustEqual newProject.description
        case None =>
          // fail here automatically because we should have retrieved the tag
          1 mustEqual 2
      }
    }

    "update project object to database" in new WithApplication {
      projectDAL.update(Json.parse(
        """{
          "name":"UpdatedProject"
        }""".stripMargin), User.superUser)(projectID)
      projectDAL.retrieveById match {
        case Some(t) =>
          t.name mustEqual "UpdatedProject"
          t.id mustEqual projectID
        case None =>
          // fail here automatically because we should have retrieved the tag
          1 mustEqual 2
      }
    }

    "delete project object in database" in new WithApplication {
      projectDAL.delete(projectID, User.superUser)
      projectDAL.retrieveById mustEqual None
    }
  }
}
