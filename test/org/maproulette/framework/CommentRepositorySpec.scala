package org.maproulette.framework

import org.maproulette.framework.model.User
import org.maproulette.framework.repository.CommentRepository
import org.maproulette.utils.TestDatabase

/**
 * @author mcuthbert
 */
class CommentRepositorySpec extends TestDatabase {
  val repository = new CommentRepository(database)

  "CommentRepository" should {
    "add comment into database" in {
      val comment = this.repository.add(User.superUser, 123, "This is a comment", Some(456))
      comment.osm_id mustEqual User.superUser.osmProfile.id
      comment.taskId mustEqual 123
      comment.actionId mustEqual 456

      val retrievedComment = this.repository.retrieve(comment.id)
      retrievedComment mustEqual comment
    }
  }
}
