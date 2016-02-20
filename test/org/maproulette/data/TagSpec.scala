package org.maproulette.data

import org.junit.runner._
import org.maproulette.data.dal.TagDAL
import org.specs2.mutable._
import org.specs2.runner._
import play.api.test._
import play.api.libs.json._

/**
  * @author cuthbertm
  */
@RunWith(classOf[JUnitRunner])
class TagSpec extends Specification {
  implicit var tagID:Long = -1

  sequential

  "Tags" should {
    "write tag object to database" in new WithApplication {
      val newTag = Tag(tagID, "NewTag", Some("This is a newTag"))
      tagID = TagDAL.insert(newTag).id
      TagDAL.retrieveById match {
        case Some(t) =>
          t.name mustEqual newTag.name.toLowerCase
          t.description mustEqual newTag.description
        case None =>
          // fail here automatically because we should have retrieved the tag
          1 mustEqual 2
      }
    }

    "update tag object to database" in new WithApplication {
      TagDAL.update(Json.parse(
        """{
          "name":"updatedTag"
        }""".stripMargin))(tagID)
      TagDAL.retrieveById match {
        case Some(t) =>
          t.name mustEqual "updatedtag"
          t.id mustEqual tagID
        case None =>
          // fail here automatically because we should have retrieved the tag
          1 mustEqual 2
      }
    }

    "delete tag object in database" in new WithApplication {
      implicit val ids = List(tagID)
      TagDAL.delete
      TagDAL.retrieveById mustEqual None
    }

    "delete multiple tag objects from database based on name" in new WithApplication {
      TagDAL.insert(Tag(-1, "tag1"))
      TagDAL.insert(Tag(-1, "tag2"))
      TagDAL.insert(Tag(-1, "tag3"))
      TagDAL.deleteFromStringList(List("tag1", "tag2")) mustEqual 2
      TagDAL.retrieveByName("tag1") mustEqual None
      TagDAL.retrieveByName("tag2") mustEqual None
      //TagDAL.retrieve("tag3") mustEqual Some(_)
    }
  }
}
