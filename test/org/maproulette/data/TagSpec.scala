package org.maproulette.data

import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._
import play.api.test._

/**
  * @author cuthbertm
  */
@RunWith(classOf[JUnitRunner])
class TagSpec extends Specification {

  "Tags" should {
    "write tag object to database" in new WithApplication {
      val newTag = Tag(-1, "NewTag", Some("This is a newTag"))
      val id = Tag.insert(newTag)
      Tag.retrieveById(id.id) match {
        case Some(t) =>
          t.name mustEqual newTag.name
          t.description mustEqual newTag.description
        case None =>
          // fail here automatically because we should have retrieved the tag
          1 mustEqual 2
      }

      "update tag object to database" in new WithApplication {
        val newTag = Tag(-1, "NewTag", Some("This is a newTag"))
        val id = Tag.insert(newTag)
        val updatedTag = newTag.copy(id.id, "UpdatedTag")
        Tag.retrieveById(id.id) match {
          case Some(t) =>
            t.name mustEqual updatedTag.name
            t.description mustEqual updatedTag.description
            t.id mustEqual updatedTag.id
          case None =>
            // fail here automatically because we should have retrieved the tag
            1 mustEqual 2
        }
      }

      "delete tag object in database" in new WithApplication {
        val newTag = Tag(-1, "NewTag", Some("This is a newTag"))
        val id = Tag.insert(newTag)
        Tag.delete(id.id)
        Tag.retrieveById(id.id) mustEqual None
      }

      "delete multiple tag objects from database based on name" in new WithApplication(app) {
        Tag.insert(Tag(-1, "tag1"))
        Tag.insert(Tag(-1, "tag2"))
        Tag.insert(Tag(-1, "tag3"))
        Tag.deleteFromStringList(List("tag1", "tag2")) mustEqual 2
        Tag.retrieveByName("tag1") mustEqual None
        Tag.retrieveByName("tag2") mustEqual None
        //Tag.retrieve("tag3") mustEqual Some(_)
      }
    }
  }
}
