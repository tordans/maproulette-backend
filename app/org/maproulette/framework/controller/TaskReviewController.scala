/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package org.maproulette.framework.controller

import javax.inject.Inject
import akka.util.ByteString
import org.maproulette.data.ActionManager
import org.maproulette.exception.NotFoundException
import org.maproulette.framework.service.{
  ChallengeListingService,
  ChallengeService,
  ProjectService,
  TaskReviewService,
  UserService,
  ServiceManager
}
import org.maproulette.framework.psql.Paging
import org.maproulette.framework.model.{Challenge, ChallengeListing, Project, User}
import org.maproulette.framework.mixins.ParentMixin
import org.maproulette.framework.repository.TaskRepository
import org.maproulette.session.{SessionManager, SearchParameters, SearchTaskParameters}
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
    taskRepository: TaskRepository,
    challengeListingService: ChallengeListingService,
    challengeService: ChallengeService,
    projectService: ProjectService,
    userService: UserService,
    components: ControllerComponents,
    serviceManager: ServiceManager
) extends AbstractController(components)
    with MapRouletteController
    with ParentMixin {

  implicit val challengeListingWrites: Writes[ChallengeListing] = Json.writes[ChallengeListing]

  /**
    * Gets and claims a task that needs to be reviewed.
    *
    * @param id Task id to work on
    * @return
    */
  def startTaskReview(id: Long): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      val task = this.taskRepository.retrieve(id) match {
        case Some(t) => t
        case None    => throw new NotFoundException(s"Task with $id not found, cannot start review.")
      }

      val result = this.service.startTaskReview(user, task)
      Ok(Json.toJson(result))
    }
  }

  /**
    * Releases a claim on a task that needs to be reviewed.
    *
    * @param id Task id to work on
    * @return
    */
  def cancelTaskReview(id: Long): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      val task = this.taskRepository.retrieve(id) match {
        case Some(t) => t
        case None    => throw new NotFoundException(s"Task with $id not found, cannot cancel review.")
      }

      val result = this.service.cancelTaskReview(user, task)
      Ok(Json.toJson(result))
    }
  }

  /**
    * Gets and claims the next task that needs to be reviewed.
    *
    * Valid search parameters include:
    * cs => "my challenge name"
    * o => "mapper's name"
    * r => "reviewer's name"
    *
    * @return Task
    */
  def nextTaskReview(
      onlySaved: Boolean = false,
      sort: String,
      order: String,
      lastTaskId: Long = -1,
      excludeOtherReviewers: Boolean = false
  ): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      SearchParameters.withSearch { implicit params =>
        // Cancel review claim of last task
        try {
          val lastTask = this.taskRepository.retrieve(lastTaskId) match {
            case Some(t) => t
            case None =>
              throw new NotFoundException(s"Task with $lastTaskId not found, cannot cancel review.")
          }
          this.service.cancelTaskReview(user, lastTask)
        } catch {
          case _: Throwable => // do nothing, it's okay if the user didn't lock the prior task
        }

        val result = this.service.nextTaskReview(
          user,
          params,
          onlySaved,
          sort,
          order,
          (if (lastTaskId == -1) None else Some(lastTaskId)),
          excludeOtherReviewers
        )
        val nextTask = result match {
          case Some(task) =>
            Ok(Json.toJson(this.service.startTaskReview(user, task)))
          case None =>
            throw new NotFoundException("No tasks found to review.")
        }

        nextTask
      }
    }
  }

  /**
    * Gets tasks where a review is requested
    *
    * @param limit The number of tasks to return
    * @param page The page number for the results
    * @param sort The column to sort
    * @param order The order direction to sort
    * @param excludeOtherReviewers exclude tasks that have been reviewed by someone else
    * @return
    */
  def getReviewRequestedTasks(
      onlySaved: Boolean = false,
      limit: Int,
      page: Int,
      sort: String,
      order: String,
      excludeOtherReviewers: Boolean = false,
      includeTags: Boolean = false
  ): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      SearchParameters.withSearch { implicit params =>
        //cs => "my challenge name"
        //o => "mapper's name"
        //r => "reviewer's name"
        val (count, result) = this.service.getReviewRequestedTasks(
          User.userOrMocked(user),
          params,
          onlySaved,
          Paging(limit, page),
          sort,
          order,
          true,
          excludeOtherReviewers
        )
        Ok(
          Json.obj(
            "total" -> count,
            "tasks" -> insertChallengeJSON(
              this.serviceManager,
              result,
              includeTags
            )
          )
        )
      }
    }
  }

  /**
    * Gets reviewed tasks where the user has reviewed or requested review
    *
    * @param allowReviewNeeded Whether we should return tasks where status is review requested also
    * @param limit The number of tasks to return
    * @param page The page number for the results
    * @param sort The column to sort
    * @param order The order direction to sort
    * @return
    */
  def getReviewedTasks(
      allowReviewNeeded: Boolean = false,
      limit: Int,
      page: Int,
      sort: String,
      order: String,
      includeTags: Boolean = false
  ): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      SearchParameters.withSearch { implicit params =>
        val (count, result) = this.serviceManager.taskReview.getReviewedTasks(
          User.userOrMocked(user),
          params,
          allowReviewNeeded,
          limit,
          page,
          sort,
          order
        )
        Ok(
          Json.obj(
            "total" -> count,
            "tasks" -> insertChallengeJSON(
              this.serviceManager,
              result,
              includeTags
            )
          )
        )
      }
    }
  }

  /**
    * Gets clusters of review tasks. Uses kmeans method in postgis.
    *
    * @param reviewTasksType Type of review tasks (1: To Be Reviewed 2: User's reviewed Tasks 3: All reviewed by users)
    * @param numberOfPoints Number of clustered points you wish to have returned
    * @param onlySaved include challenges that have been saved
    * @param excludeOtherReviewers exclude tasks that have been reviewed by someone else
    *
    * @return A list of ClusteredPoint's that represent clusters of tasks
    */
  def getReviewTaskClusters(
      reviewTasksType: Int,
      numberOfPoints: Int,
      onlySaved: Boolean = false,
      excludeOtherReviewers: Boolean = false
  ): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      SearchParameters.withSearch { implicit params =>
        Ok(
          Json.toJson(
            this.serviceManager.taskReview.getReviewTaskClusters(
              User.userOrMocked(user),
              reviewTasksType,
              params,
              numberOfPoints,
              onlySaved,
              excludeOtherReviewers
            )
          )
        )
      }
    }
  }

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
    * Gets tasks near the given task id within the given challenge
    *
    * @param challengeId  The challenge id that is the parent of the tasks that you would be searching for
    * @param proximityId  Id of task for which nearby tasks are desired
    * @param excludeSelfLocked Also exclude tasks locked by requesting user
    * @param limit        The maximum number of nearby tasks to return
    * @return
    */
  def getNearbyReviewTasks(
      proximityId: Long,
      limit: Int,
      excludeOtherReviewers: Boolean = false,
      onlySaved: Boolean = false
  ): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      SearchParameters.withSearch { params =>
        val results = this.service
          .getNearbyReviewTasks(
            User.userOrMocked(user),
            params,
            proximityId,
            limit,
            excludeOtherReviewers,
            onlySaved
          )
        Ok(Json.toJson(results))
      }
    }
  }
}
