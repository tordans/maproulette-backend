/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.service

import javax.inject.{Inject, Singleton}
import org.maproulette.models.Task
import org.maproulette.framework.model._
import org.maproulette.framework.psql.Query
import org.maproulette.framework.psql.filter.BaseParameter
import org.maproulette.framework.repository.ChallengeListingRepository
import org.maproulette.framework.psql.filter._
import org.maproulette.framework.psql._
import org.maproulette.framework.service.ServiceHelper

/**
  * Service layer for ChallengeListings to handle all the challenge listing business logic
  *
  * @author krotstan
  */
@Singleton
class ChallengeListingService @Inject() (repository: ChallengeListingRepository)
    extends ServiceHelper
    with ServiceMixin[ChallengeListing] {
  def retrieve(parentId: Long): Option[ChallengeListing] =
    this.query(Query.simple(List(BaseParameter(Challenge.FIELD_PARENT_ID, parentId)))).headOption

  def query(query: Query): List[ChallengeListing] = this.repository.query(query)

  /**
    * Returns a list of challenges that have reviews/review requests.
    *
    * @param reviewTasksType  The type of reviews (1: To Be Reviewed,  2: User's reviewed Tasks, 3: All reviewed by users)
    * @param user The user making request (for challenge permission visibility)
    * @param taskStatus The task statuses to include
    * @param excludeOtherReviewers Whether tasks completed by other reviewers should be included
    * @return A list of children listing objects
    */
  def withReviewList(
      reviewTasksType: Int,
      user: User,
      taskStatus: Option[List[Int]] = None,
      excludeOtherReviewers: Boolean = false,
      paging: Paging = Paging()
  ): List[ChallengeListing] = {
    val filter =
      Filter(
        List(
          FilterGroup(
            List(
              // Has a task review
              BaseParameter("task_review.id", "", Operator.NULL, negate = true),
              // Task Status in list if given a list of task statuses
              FilterParameter.conditional(
                "t.status",
                taskStatus.getOrElse(List.empty),
                Operator.IN,
                includeOnlyIfTrue = taskStatus.nonEmpty
              ),
              // review_requested_by != user.id unless a super user
              // to be reviewed tasks (review type = 1)
              FilterParameter.conditional(
                "task_review.review_requested_by",
                user.id,
                negate = true,
                includeOnlyIfTrue = (reviewTasksType == 1) && !user.isSuperUser
              ),
              // reviewed_by == user.id for 'tasks reviewed by me' (review type = 3)
              FilterParameter.conditional(
                "task_review.reviewed_by",
                user.id,
                includeOnlyIfTrue = (reviewTasksType == 2)
              ),
              // reviewed_by == user.id for 'my reviewed tasks' (review type = 2)
              FilterParameter.conditional(
                "task_review.review_requested_by",
                user.id,
                includeOnlyIfTrue = (reviewTasksType == 3)
              ),
              // review status = requested or disputed if reviewTasksType = 1
              FilterParameter.conditional(
                "task_review.review_status",
                List(Task.REVIEW_STATUS_REQUESTED, Task.REVIEW_STATUS_DISPUTED),
                Operator.IN,
                includeOnlyIfTrue = (reviewTasksType == 1)
              )
            )
          ),
          // Check project/challenge visiblity
          this.challengeVisibilityFilter(user),
          // reviewed_by is empty or user.id if excludeOtherReviewers
          FilterGroup(
            List(
              BaseParameter("task_review.reviewed_by", "", Operator.NULL),
              BaseParameter("task_review.reviewed_by", user.id)
            ),
            OR(),
            (excludeOtherReviewers && reviewTasksType == 1)
          )
        )
      )

    val query = Query(
      filter,
      paging = paging,
      grouping = Grouping("c.id")
    )
    this.query(query)
  }
}
