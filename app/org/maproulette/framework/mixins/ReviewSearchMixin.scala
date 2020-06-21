/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package org.maproulette.framework.mixins

import org.maproulette.session.SearchParameters
import org.maproulette.framework.psql.SQLUtils
import org.maproulette.framework.psql.filter._
import org.maproulette.framework.psql.Query
import org.maproulette.framework.psql.{AND, OR}
import org.maproulette.framework.model.{TaskReview, Project, Challenge, User}
import org.maproulette.permissions.Permission

import org.maproulette.models.Task

trait ReviewSearchMixin extends SearchParametersMixin {
  val REVIEW_REQUESTED_TASKS = 1 // Tasks needing to be reviewed
  val MY_REVIEWED_TASKS      = 2 // Tasks reviewed by user
  val REVIEWED_TASKS_BY_ME   = 3 // Tasks completed by user and done review
  val ALL_REVIEWED_TASKS     = 4 // All review(ed) tasks

  /**
    * Setup the search clauses for searching the review tables
    */
  def setupReviewSearchClause(
      query: Query,
      user: User,
      permission: Permission,
      searchParameters: SearchParameters,
      reviewTasksType: Int = ALL_REVIEWED_TASKS,
      includeDisputed: Boolean = true,
      onlySaved: Boolean = false,
      excludeOtherReviewers: Boolean = false
  ): Query = {
    var newQuery = query
    newQuery = newQuery.addFilterGroup(this.userHasPermission(user, permission, reviewTasksType))

    // Filter bundles to only primary tasks
    newQuery = newQuery.addFilterGroup(
      FilterGroup(
        List(
          BaseParameter(
            Task.FIELD_BUNDLE_ID,
            None,
            Operator.NULL,
            table = Some(Task.TABLE)
          ),
          BaseParameter(
            Task.FIELD_BUNDLE_PRIMARY,
            true,
            Operator.BOOL,
            table = Some(Task.TABLE)
          )
        ),
        OR()
      )
    )

    // Exclude unnecessary reviews
    newQuery = newQuery.addFilterGroup(
      FilterGroup(
        List(
          BaseParameter(
            TaskReview.FIELD_REVIEW_STATUS,
            Task.REVIEW_STATUS_UNNECESSARY,
            Operator.NE,
            table = Some(TaskReview.TABLE)
          )
        )
      )
    )

    if (reviewTasksType == REVIEW_REQUESTED_TASKS) {
      // Limit to only review requested and disputed
      newQuery = newQuery.addFilterGroup(
        FilterGroup(
          List(
            BaseParameter(
              TaskReview.FIELD_REVIEW_STATUS,
              Task.REVIEW_STATUS_REQUESTED,
              Operator.EQ,
              table = Some(TaskReview.TABLE)
            ),
            FilterParameter.conditional(
              TaskReview.FIELD_REVIEW_STATUS,
              Task.REVIEW_STATUS_DISPUTED,
              Operator.EQ,
              includeOnlyIfTrue = includeDisputed,
              table = Some(TaskReview.TABLE)
            )
          ),
          OR()
        )
      )

      // Only challenges 'saved' (marked as favorite) by this user
      if (onlySaved) {
        newQuery = newQuery.addFilterGroup(
          FilterGroup(
            List(
              SubQueryFilter(
                Challenge.FIELD_ID,
                Query.simple(
                  List(
                    BaseParameter(
                      "user_id",
                      user.id,
                      Operator.EQ,
                      useValueDirectly = true,
                      table = Some("sc")
                    )
                  ),
                  "SELECT challenge_id from saved_challenges sc"
                ),
                false,
                Operator.IN,
                Some("c")
              )
            )
          )
        )
      }

      // Don't show tasks already reviewed by someone else
      // Used most often when tasks need a "re-review" (so in a requested state)
      if (excludeOtherReviewers) {
        newQuery = newQuery.addFilterGroup(
          FilterGroup(
            List(
              BaseParameter(
                TaskReview.FIELD_REVIEWED_BY,
                null,
                Operator.NULL,
                table = Some(TaskReview.TABLE)
              ),
              BaseParameter(
                TaskReview.FIELD_REVIEWED_BY,
                user.id,
                Operator.EQ,
                useValueDirectly = true,
                table = Some(TaskReview.TABLE)
              )
            ),
            OR()
          )
        )
      }
    } else if (reviewTasksType == MY_REVIEWED_TASKS) {
      // Limit to already reviewed tasks
      newQuery = newQuery.addFilterGroup(
        FilterGroup(
          List(
            BaseParameter(
              TaskReview.FIELD_REVIEW_STATUS,
              Task.REVIEW_STATUS_REQUESTED,
              Operator.NE,
              table = Some(TaskReview.TABLE)
            )
          )
        )
      )
    }

    newQuery = newQuery.addFilterGroup(
      FilterGroup(
        List(
          FilterParameter.conditional(
            TaskReview.FIELD_REVIEW_STATUS,
            searchParameters.taskParams.taskReviewStatus.getOrElse(List()).mkString(","),
            Operator.IN,
            searchParameters.invertFields.getOrElse(List()).contains("trStatus"),
            true,
            searchParameters.taskParams.taskReviewStatus.getOrElse(List()).nonEmpty,
            table = Some(TaskReview.TABLE)
          )
        )
      )
    )

    newQuery = newQuery.addFilterGroup(this.filterProjects(searchParameters))
    newQuery = newQuery.addFilterGroup(this.filterProjectEnabled(searchParameters))
    newQuery = newQuery.addFilterGroup(this.filterChallenges(searchParameters))
    newQuery = newQuery.addFilterGroup(this.filterChallengeEnabled(searchParameters))
    newQuery = newQuery.addFilterGroup(this.filterOwner(searchParameters))
    newQuery = newQuery.addFilterGroup(this.filterReviewer(searchParameters))
    newQuery = newQuery.addFilterGroup(this.filterMapper(searchParameters))
    newQuery = newQuery.addFilterGroup(this.filterTaskStatus(searchParameters, List()))
    newQuery = newQuery.addFilterGroup(this.filterReviewMappers(searchParameters))
    newQuery = newQuery.addFilterGroup(this.filterReviewers(searchParameters))
    newQuery = newQuery.addFilterGroup(this.filterTaskPriorities(searchParameters))
    newQuery = newQuery.addFilterGroup(this.filterLocation(searchParameters))
    newQuery = newQuery.addFilterGroup(this.filterProjectSearch(searchParameters))
    newQuery = newQuery.addFilterGroup(this.filterTaskId(searchParameters))
    newQuery = newQuery.addFilterGroup(this.filterPriority(searchParameters))
    newQuery = newQuery.addFilterGroup(this.filterTaskTags(searchParameters))
    newQuery = newQuery.addFilterGroup(this.filterReviewDate(searchParameters))

    newQuery
  }

  private def userHasPermission(
      user: User,
      permission: Permission,
      reviewTasksType: Int
  ): FilterGroup = {
    if (permission.isSuperUser(user)) return FilterGroup(List())

    // Setup permissions
    // You see review task when:
    // 1. Project and Challenge enabled (so visible to everyone)
    // 2. You manage the project (your user group matches groups of project)
    // 3. You worked on the task - ie. review/reviewed it
    //    (unless it's a review request as you can't review tasks you requested)

    val enabledFilter =
      FilterGroup(
        List(
          BaseParameter(Project.FIELD_ENABLED, true, Operator.BOOL, table = Some("p")),
          BaseParameter(Challenge.FIELD_ENABLED, true, Operator.BOOL, table = Some("c"))
        ),
        AND()
      )

    val managesProjectFilter =
      // user manages project ids
      FilterGroup(
        List(
          BaseParameter(
            Project.FIELD_ID,
            user.managedProjectIds().mkString(","),
            Operator.IN,
            useValueDirectly = true,
            table = Some("p")
          )
        )
      )

    if (reviewTasksType == REVIEW_REQUESTED_TASKS) {
      // If not a super user than you are not allowed to request reviews
      // on tasks you completed.
      FilterGroup(
        List(
          CustomParameter(
            "(" +
              Filter(
                List(
                  enabledFilter,
                  managesProjectFilter
                ),
                OR()
              ).sql() + ")"
          ),
          BaseParameter(
            TaskReview.FIELD_REVIEW_REQUESTED_BY,
            user.id,
            Operator.NE,
            useValueDirectly = true,
            table = Some(TaskReview.TABLE)
          )
        ),
        AND()
      )
    } else {
      FilterGroup(
        List(
          CustomParameter(
            "(" +
              Filter(
                List(
                  enabledFilter,
                  managesProjectFilter,
                  FilterGroup(
                    List(
                      BaseParameter(
                        TaskReview.FIELD_REVIEW_REQUESTED_BY,
                        user.id,
                        Operator.EQ,
                        useValueDirectly = true,
                        table = Some(TaskReview.TABLE)
                      )
                    )
                  ),
                  FilterGroup(
                    List(
                      BaseParameter(
                        TaskReview.FIELD_REVIEWED_BY,
                        user.id,
                        Operator.EQ,
                        useValueDirectly = true,
                        table = Some(TaskReview.TABLE)
                      )
                    )
                  )
                ),
                OR()
              ).sql() + ")"
          )
        )
      )
    }
  }

}
