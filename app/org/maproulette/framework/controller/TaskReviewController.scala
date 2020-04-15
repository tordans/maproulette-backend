/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package org.maproulette.framework.controller

import javax.inject.Inject
import org.maproulette.data.ActionManager
import org.maproulette.framework.service.{ChallengeListingService, ProjectService}
import org.maproulette.framework.psql.Paging
import org.maproulette.framework.model.{Challenge, ChallengeListing, Project}
import org.maproulette.session.SessionManager
import org.maproulette.utils.Utils
import play.api.mvc._
import play.api.libs.json._

/**
  * TaskReviewController is responsible for handling functionality related to
  * task reviews.
  *
  * @author krotstan
  */
class TaskReviewController @Inject() (
    override val sessionManager: SessionManager,
    override val actionManager: ActionManager,
    override val bodyParsers: PlayBodyParsers,
    challengeListingService: ChallengeListingService,
    projectService: ProjectService,
    components: ControllerComponents
) extends AbstractController(components)
    with MapRouletteController {

  implicit val challengeListingWrites: Writes[ChallengeListing] = Json.writes[ChallengeListing]

  /**
    * Returns a list of challenges that have reviews/review requests.
    *
    * @param reviewTasksType  The type of reviews (1: To Be Reviewed,  2: User's reviewed Tasks, 3: All reviewed by users)
    * @param tStatus The task statuses to include
    * @param excludeOtherReviewers Whether tasks completed by other reviewers should be included
    * @return JSON challenge list
    */
  def listChallenges(
      reviewTasksType: Int,
      tStatus: String,
      excludeOtherReviewers: Boolean = false,
      limit: Int,
      page: Int
  ): Action[AnyContent] =
    Action.async { implicit request =>
      this.sessionManager.authenticatedRequest { implicit user =>
        val taskStatus = tStatus match {
          case v if v.nonEmpty => Utils.toIntList(v)
          case _               => None
        }

        val challenges = this.challengeListingService.withReviewList(
          reviewTasksType,
          user,
          taskStatus,
          excludeOtherReviewers,
          Paging(limit, page)
        )

        // Populate some parent/virtual parent project data
        val projects = Some(
          this.projectService
            .list(challenges.map(c => c.parent))
            .map(p => p.id -> p)
            .toMap
        )

        var vpIds = scala.collection.mutable.Set[Long]()
        challenges.map(c => {
          c.virtualParents match {
            case Some(vps) =>
              vps.map(vp => vpIds += vp)
            case _ => // do nothing
          }
        })
        val vpObjects =
          this.projectService.list(vpIds.toList).map(p => p.id -> p).toMap

        val jsonList = challenges.map { c =>
          val projectJson = Json
            .toJson(projects.get(c.parent))
            .as[JsObject] - Project.KEY_GROUPS

          var updated =
            Utils.insertIntoJson(Json.toJson(c), Challenge.KEY_PARENT, projectJson, true)
          c.virtualParents match {
            case Some(vps) =>
              val vpJson =
                Some(
                  vps.map(vp => Json.toJson(vpObjects.get(vp)).as[JsObject] - Project.KEY_GROUPS)
                )
              updated = Utils.insertIntoJson(updated, Challenge.KEY_VIRTUAL_PARENTS, vpJson, true)
            case _ => // do nothing
          }
          updated
        }

        Ok(
          Json.toJson(jsonList)
        )
      }
    }
}
