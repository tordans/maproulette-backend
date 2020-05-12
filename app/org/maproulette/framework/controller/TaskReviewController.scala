/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package org.maproulette.framework.controller

import javax.inject.Inject
import akka.util.ByteString
import org.maproulette.data.ActionManager
import org.maproulette.framework.service.{
  ChallengeListingService,
  ProjectService,
  TaskReviewService,
  UserService
}
import org.maproulette.framework.psql.Paging
import org.maproulette.framework.model.{Challenge, ChallengeListing, Project, User}
import org.maproulette.session.{SessionManager, SearchParameters}
import org.maproulette.utils.Utils
import play.api.mvc._
import play.api.libs.json._
import play.api.http.HttpEntity

import org.maproulette.models.Task

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
    service: TaskReviewService,
    challengeListingService: ChallengeListingService,
    projectService: ProjectService,
    userService: UserService,
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
            .as[JsObject] - Project.KEY_GRANTS

          var updated =
            Utils.insertIntoJson(Json.toJson(c), Challenge.KEY_PARENT, projectJson, true)
          c.virtualParents match {
            case Some(vps) =>
              val vpJson =
                Some(
                  vps.map(vp => Json.toJson(vpObjects.get(vp)).as[JsObject] - Project.KEY_GRANTS)
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

  /**
    * Gets reviewed tasks where the user has reviewed or requested review
    *
    * @param reviewTasksType - 1: To Be Reviewed 2: User's reviewed Tasks 3: All reviewed by users
    * @param startDate Optional start date to filter by reviewedAt date
    * @param endDate Optional end date to filter by reviewedAt date
    * @param onlySaved Only include saved challenges
    * @param excludeOtherReviewers exclude tasks that have been reviewed by someone else
    * @return
    */
  def getReviewMetrics(
      reviewTasksType: Int,
      mappers: String = "",
      reviewers: String = "",
      priorities: String = "",
      startDate: String = null,
      endDate: String = null,
      onlySaved: Boolean = false,
      excludeOtherReviewers: Boolean = false,
      includeByPriority: Boolean = false,
      includeByTaskStatus: Boolean = false
  ): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      SearchParameters.withSearch { implicit params =>
        val result = this.service.getReviewMetrics(
          User.userOrMocked(user),
          reviewTasksType,
          params,
          Some(Utils.split(mappers)),
          Some(Utils.split(reviewers)),
          Utils.toIntList(priorities),
          startDate,
          endDate,
          onlySaved,
          excludeOtherReviewers
        )

        if (includeByPriority || includeByTaskStatus) {
          var resultJson: JsValue = Json.obj(
            "reviewActions" -> Json.toJson(result)
          )

          if (includeByPriority) {
            val priorityMap = this.fetchPriorityReviewMetrics(
              User.userOrMocked(user),
              reviewTasksType,
              params,
              Some(Utils.split(mappers)),
              Some(Utils.split(reviewers)),
              startDate,
              endDate,
              onlySaved,
              excludeOtherReviewers
            )

            resultJson =
              Utils.insertIntoJson(resultJson, "priorityReviewActions", Json.toJson(priorityMap))
          }

          if (includeByTaskStatus) {
            val statusMap = this.fetchByTaskStatusReviewMetrics(
              User.userOrMocked(user),
              reviewTasksType,
              params,
              Some(Utils.split(mappers)),
              Some(Utils.split(reviewers)),
              Utils.toIntList(priorities),
              startDate,
              endDate,
              onlySaved,
              excludeOtherReviewers
            )

            resultJson =
              Utils.insertIntoJson(resultJson, "statusReviewActions", Json.toJson(statusMap))
          }

          Ok(resultJson)
        } else {
          Ok(Json.toJson(List(result)))
        }
      }
    }
  }

  private def fetchPriorityReviewMetrics(
      user: User,
      reviewTasksType: Int,
      params: SearchParameters,
      mappers: Option[List[String]],
      reviewers: Option[List[String]],
      startDate: String,
      endDate: String,
      onlySaved: Boolean,
      excludeOtherReviewers: Boolean
  ): scala.collection.mutable.Map[String, JsValue] = {
    val prioritiesToFetch =
      List(Challenge.PRIORITY_HIGH, Challenge.PRIORITY_MEDIUM, Challenge.PRIORITY_LOW)

    val priorityMap = scala.collection.mutable.Map[String, JsValue]()

    prioritiesToFetch.foreach(p => {
      val pResult = this.service.getReviewMetrics(
        user,
        reviewTasksType,
        params,
        mappers,
        reviewers,
        Some(List(p)),
        startDate,
        endDate,
        onlySaved,
        excludeOtherReviewers
      )

      priorityMap.put(p.toString, Json.toJson(pResult))
    })

    priorityMap
  }

  private def fetchByTaskStatusReviewMetrics(
      user: User,
      reviewTasksType: Int,
      params: SearchParameters,
      mappers: Option[List[String]],
      reviewers: Option[List[String]],
      priorities: Option[List[Int]] = None,
      startDate: String,
      endDate: String,
      onlySaved: Boolean,
      excludeOtherReviewers: Boolean
  ): scala.collection.mutable.Map[String, JsValue] = {
    val statusesToFetch =
      List(
        Task.STATUS_FIXED,
        Task.STATUS_FALSE_POSITIVE,
        Task.STATUS_ALREADY_FIXED,
        Task.STATUS_TOO_HARD
      )

    val statusMap = scala.collection.mutable.Map[String, JsValue]()

    statusesToFetch.foreach(m => {
      val newParams = params.copy(
        taskStatus = Some(List(m))
      )

      val mResult = this.service.getReviewMetrics(
        user,
        reviewTasksType,
        newParams,
        mappers,
        reviewers,
        priorities,
        startDate,
        endDate,
        onlySaved,
        excludeOtherReviewers
      )

      statusMap.put(m.toString, Json.toJson(mResult))
    })

    statusMap
  }

  /**
    * Returns a CSV export of review metrics per mapper.
    *
    * @param mappers Optional limit to reviews of tasks by specific mappers
    * @param reviewers Optional limit reviews done by specific reviewers
    * @param priorities Optional limit to only these priorities
    * @param startDate Optional start date to filter by reviewedAt date
    * @param endDate Optional end date to filter by reviewedAt date
    * @param onlySaved Only include saved challenges
    * @return
    */
  def extractMapperMetrics(
      mappers: String = "",
      reviewers: String = "",
      priorities: String = "",
      startDate: String = null,
      endDate: String = null,
      onlySaved: Boolean = false
  ): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      SearchParameters.withSearch { implicit params =>
        val metrics = this.service.getMapperMetrics(
          User.userOrMocked(user),
          params,
          Some(Utils.split(mappers)),
          Some(Utils.split(reviewers)),
          Utils.toIntList(priorities),
          startDate,
          endDate,
          onlySaved
        )

        val mapperNames =
          this.userService
            .retrieveListById(metrics.map(m => m.userId.get), Paging())
            .map(u => u.id -> u.name)
            .toMap

        val seqString = metrics.map(row => {
          var mapper = mapperNames.get(row.userId.get)

          val reviewTimeSeconds = Math.round(row.avgReviewTime / 1000)

          s"${mapper.get},${row.total},${reviewTimeSeconds},${row.reviewRequested}," +
            s"${row.reviewApproved},${row.reviewRejected},${row.reviewAssisted}," +
            s"${row.reviewDisputed}"
        })

        Result(
          header = ResponseHeader(
            OK,
            Map(CONTENT_DISPOSITION -> s"attachment; filename=mapper_review_metrics.csv")
          ),
          body = HttpEntity.Strict(
            ByteString(
              s"""Mapper,Total Review Tasks,Avg Review Time (seconds),Review Requested,Approved,Needs Revision,Approved w/Fixes,Contested\n"""
            ).concat(ByteString(seqString.mkString("\n"))),
            Some("text/csv; header=present")
          )
        )
      }
    }
  }
}
