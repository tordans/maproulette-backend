/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package org.maproulette.framework.controller

import javax.inject.Inject
import org.maproulette.data.ActionManager
import org.maproulette.framework.service.TaskService
import org.maproulette.framework.psql.Paging
import org.maproulette.framework.model.User
import org.maproulette.session.SessionManager
import org.maproulette.utils.Utils
import play.api.mvc._
import play.api.libs.json._
import org.maproulette.exception.NotFoundException

/**
  * TaskController is responsible for handling functionality related to
  * tasks.
  *
  * @author krotstan
  */
class TaskController @Inject() (
    override val sessionManager: SessionManager,
    override val actionManager: ActionManager,
    override val bodyParsers: PlayBodyParsers,
    taskService: TaskService,
    components: ControllerComponents
) extends AbstractController(components)
    with MapRouletteController {

  /**
    * Updates the completion responses asked in the task instructions. Request
    * body should include the reponse JSON.
    *
    * @param id    The id of the task
    */
  def updateCompletionResponses(id: Long): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      val responses = request.body.asJson match {
        case Some(r) => r
        case None =>
          throw new NotFoundException(s"Completion responses not found in request body.")
      }

      this.taskService.updateCompletionResponses(id, user, responses)
      NoContent
    }
  }
}
