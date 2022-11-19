/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.controller

import java.net.URLDecoder

import javax.inject.Inject
import org.maproulette.data.ActionManager
import org.maproulette.framework.service.{CommentService, ServiceManager}
import org.maproulette.session.SessionManager
import play.api.libs.json._
import play.api.mvc._

/**
  * @author mcuthbert
  */
class CommentController @Inject() (
    override val sessionManager: SessionManager,
    override val actionManager: ActionManager,
    override val bodyParsers: PlayBodyParsers,
    commentService: CommentService,
    components: ControllerComponents,
    serviceManager: ServiceManager
) extends AbstractController(components)
    with MapRouletteController {

  /**
    * Retrieves a specific comment for the user
    *
    * @param commentId The id of the comment to retrieve
    * @return The comment
    */
  def retrieve(commentId: Long): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      this.commentService.retrieve(commentId) match {
        case Some(comment) => Ok(Json.toJson(comment))
        case None          => NotFound
      }
    }
  }

  /**
    * Retrieves all the comments for a Task
    *
    * @param taskId The task to retrieve the comments for
    * @return A list of comments
    */
  def find(taskId: Long): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      Ok(
        Json.toJson(this.commentService.find(List.empty, List.empty, List(taskId)))
      )
    }
  }

  /**
    * Retrieves all the challenge comments for a Challenge
    *
    * @param challengeId The challenge to retrieve the comments for
    * @return A list of comments
    */
  def findChallengeComments(challengeId: Long): Action[AnyContent] = Action.async {
    implicit request =>
      this.sessionManager.userAwareRequest { implicit user =>
        Ok(
          Json.toJson(this.commentService.findChallengeComments(challengeId))
        )
      }
  }

  /**
    * Adds a comment for a specific task
    *
    * @param taskId   The id for a task
    * @body comment  The comment the user is leaving
    * @param actionId The action if any associated with the comment
    * @return Ok if successful.
    */
  def add(taskId: Long, actionId: Option[Long]): Action[JsValue] = Action.async(bodyParsers.json) {
    implicit request =>
      this.sessionManager.authenticatedRequest { implicit user =>
        val comment = (request.body \ "comment").asOpt[String].getOrElse("");
        Created(
          Json.toJson(
            this.commentService.create(user, taskId, comment, actionId)
          )
        )
      }
  }

  /**
    * Adds a comment for a specific challenge
    *
    * @param challengeId   The id for a challenge
    * @body comment  The comment the user is leaving
    * @return Ok if successful.
    */
  def addChallengeComment(challengeId: Long): Action[JsValue] = Action.async(bodyParsers.json) {
    implicit request =>
      this.sessionManager.authenticatedRequest { implicit user =>
        val comment = (request.body \ "comment").asOpt[String].getOrElse("");
        Created(
          Json.toJson(
            this.commentService
              .createChallengeComment(user, challengeId, comment)
          )
        )
      }
    }

  /**
    * Adds a comment for tasks in a bundle
    *
    * @param bundleId   The id for the bundle
    * @body comment  The comment the user is leaving
    * @param actionId The action if any associated with the comment
    * @return Ok if successful.
    */
  def addToBundleTasks(
      bundleId: Long,
      actionId: Option[Long]
  ): Action[JsValue] = Action.async(bodyParsers.json) { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      val comment = (request.body \ "comment").asOpt[String].getOrElse("");
      this.commentService.addToBundle(user, bundleId, comment, actionId)

      Ok(Json.toJson(this.serviceManager.taskBundle.getTaskBundle(user, bundleId)))
    }
  }

  /**
    * Updates the original comment
    *
    * @param commentId The ID of the comment to update
    * @body comment   The comment to update
    * @return
    */
  def update(commentId: Long): Action[JsValue] = Action.async(bodyParsers.json) {
    implicit request =>
      this.sessionManager.authenticatedRequest { implicit user =>
        val comment = (request.body \ "comment").asOpt[String].getOrElse("");
        Ok(
          Json.toJson(
            this.commentService.update(commentId, comment, user)
          )
        )
      }
  }

  /**
    * Deletes a comment from a task
    *
    * @param taskId    The id of the task that the comment is associated with
    * @param commentId The id of the comment that is being deleted
    * @return Ok if successful,
    */
  def delete(taskId: Long, commentId: Long): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      this.commentService.delete(taskId, commentId, user)
      Ok
    }
  }
}
