package org.maproulette.framework.repository

import java.sql.Connection

import anorm._
import javax.inject.Inject
import org.maproulette.framework.model.{Comment, User}
import org.maproulette.framework.psql.Query
import org.maproulette.framework.psql.filter.BaseFilterParameter
import org.maproulette.framework.service.GroupService
import play.api.db.Database

/**
  * Repository to handle all the database queries for the Comment object
  *
  * @author mcuthbert
  */
class CommentRepository @Inject() (override val db: Database)
    extends RepositoryMixin {

  /**
    * Query function that allows a user to build their own query against the Comment table
    *
    * @param query The query to execute
    * @param c An implicit connection
    * @return A list of returned Comments
    */
  def query(query: Query)(implicit c: Option[Connection] = None): List[Comment] = {
    withMRConnection { implicit c =>
      query
        .build(s"""SELECT * FROM task_comments tc
          INNER JOIN users u ON u.osm_id = tc.osm_id""")
        .as(CommentRepository.parser.*)
    }
  }

  /**
    * Updates a comment that a user previously set
    *
    * @param id      The id for the original comment
    * @param updatedComment The new comment
    * @param c              Implicit provided optional connection
    * @return The updated comment
    */
  def update(id: Long, updatedComment: String)(
      implicit c: Option[Connection] = None
  ): Comment = {
    withMRTransaction { implicit c =>
      SQL("UPDATE task_comments SET comment = {comment} WHERE id = {id} RETURNING *")
        .on(Symbol("comment") -> updatedComment, Symbol("id") -> id)
        .as(CommentRepository.parser.*)
        .head
    }
  }

  /**
    * Retrieves a specific comment
    *
    * @param id The id for the comment
    * @param c         Implicit provided optional connection
    * @return An optional comment
    */
  def retrieve(id: Long)(implicit c: Option[Connection] = None): Option[Comment] = {
    this.withMRConnection { implicit c =>
      Query
        .simple(List(BaseFilterParameter("tc.id", id)))
        .build("""SELECT * FROM task_comments tc
              INNER JOIN users u ON u.osm_id = tc.osm_id""")
        .as(CommentRepository.parser.*)
        .headOption
    }
  }

  /**
    * Deletes a comment from the database
    *
    * @param commentId The id for the comment being deleted
    * @param c         Implicit provided optional connection
    */
  def delete(commentId: Long)(
      implicit c: Option[Connection] = None
  ): Boolean = {
    withMRConnection { implicit c =>
      Query
        .simple(List(BaseFilterParameter("id", commentId)))
        .build("DELETE FROM task_comments")
        .execute()
    }
  }

  /**
    * Retrieves all the comments for a task, challenge or project
    *
    * @param query The query to match against to retrieve the comments
    * @param c               Implicit provided optional connection
    * @return The list of comments for the task
    */
  def find(query: Query)(implicit c: Option[Connection] = None): List[Comment] = {
    withMRConnection { implicit c =>
      query.build("""
              SELECT * FROM task_comments tc
              INNER JOIN users u ON u.osm_id = tc.osm_id""").as(CommentRepository.parser.*)
    }
  }

  /**
    * Add comment to a task
    *
    * @param user     The user adding the comment
    * @param taskId     Id of the task that is having the comment added too
    * @param comment  The actual comment
    * @param actionId the id for the action if any action associated
    * @param c        Implicit provided optional connection
    */
  def add(user: User, taskId: Long, comment: String, actionId: Option[Long])(
      implicit c: Option[Connection] = None
  ): Comment = {
    this.withMRTransaction { implicit c =>
      val query =
        s"""
           |INSERT INTO task_comments (osm_id, task_id, comment, action_id)
           |VALUES ({osm_id}, {task_id}, {comment}, {action_id})
           |RETURNING *
         """.stripMargin
      SQL(query)
        .on(
          Symbol("osm_id")    -> user.osmProfile.id,
          Symbol("task_id")   -> taskId,
          Symbol("comment")   -> comment,
          Symbol("action_id") -> actionId
        )
        .as(CommentRepository.parser.*)
        .head
    }
  }
}

object CommentRepository {
  val parser: RowParser[Comment] = Macro.parser[Comment](
    "task_comments.id",
    "task_comments.osm_id",
    "users.name",
    "task_comments.task_id",
    "task_comments.challenge_id",
    "task_comments.project_id",
    "task_comments.created",
    "task_comments.comment",
    "task_comments.action_id"
  )
}
