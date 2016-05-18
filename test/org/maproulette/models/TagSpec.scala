package org.maproulette.models

import javax.inject.Inject

import org.junit.runner._
import org.maproulette.models.dal.TagDAL
import org.maproulette.session.User
import org.specs2.mutable._
import org.specs2.runner._
import play.api.test._
import play.api.libs.json._

/**
  * @author cuthbertm
  */
@RunWith(classOf[JUnitRunner])
class TagSpec @Inject() (tagDAL: TagDAL) extends Specification {
  implicit var tagID:Long = -1

  sequential

  "Tags" should {
    "write tag object to database" in new WithApplication {
      val newTag = Tag(tagID, "NewTag", Some("This is a newTag"))
      tagID = tagDAL.insert(newTag, User.superUser).id
      tagDAL.retrieveById match {
        case Some(t) =>
          t.name mustEqual newTag.name.toLowerCase
          t.description mustEqual newTag.description
        case None =>
          // fail here automatically because we should have retrieved the tag
          1 mustEqual 2
      }
    }

    "update tag object to database" in new WithApplication {
      tagDAL.update(Json.parse(
        """{
          "name":"updatedTag"
        }""".stripMargin), User.superUser)(tagID)
      tagDAL.retrieveById match {
        case Some(t) =>
          t.name mustEqual "updatedtag"
          t.id mustEqual tagID
        case None =>
          // fail here automatically because we should have retrieved the tag
          1 mustEqual 2
      }
    }

    "delete tag object in database" in new WithApplication {
      tagDAL.delete(tagID, User.superUser)
      tagDAL.retrieveById mustEqual None
    }
  }
}
