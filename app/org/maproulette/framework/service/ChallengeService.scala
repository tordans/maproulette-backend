/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.service

import javax.inject.{Inject, Singleton}
import org.maproulette.permissions.Permission
import org.maproulette.framework.model.{Challenge, Grant, Project, User}
import org.maproulette.data.{UserType, ProjectType}
import org.maproulette.framework.psql.{Query, OR, Paging}
import org.maproulette.framework.psql.filter._
import org.maproulette.framework.repository.ChallengeRepository

/**
  * Service layer for Challenges to handle all the challenge business logic
  *
  * @author mcuthbert
  */
@Singleton
class ChallengeService @Inject() (
    repository: ChallengeRepository,
    permission: Permission
) extends ServiceMixin[Challenge] {

  def retrieve(id: Long): Option[Challenge] =
    this.query(Query.simple(List(BaseParameter(Challenge.FIELD_ID, id)))).headOption

  def list(ids: List[Long], paging: Paging = Paging()): List[Challenge] = {
    if (ids.isEmpty) {
      return List()
    }

    this.query(
      Query.simple(
        List(
          BaseParameter(Challenge.FIELD_ID, ids, Operator.IN)
        ),
        paging = paging
      )
    )
  }

  def query(query: Query): List[Challenge] = this.repository.query(query)

  /**
    * Returns a FilterGroup that creates a subquery to filter challengs
    * to include only ones visible to the user. This means the user is
    * a superuser or owner of the parent Project, or:
    *  - Both Challenge and parent Project are enabled
    *  - User is granted a role on the parent Project
    *
    * @param - User to check visibility for.
    */
  def challengeVisibilityFilter(user: User): FilterGroup = {
    FilterGroup(
      List(
        SubQueryFilter(
          "",
          Query.simple(
            List(
              BaseParameter(Project.FIELD_ENABLED, "", Operator.BOOL, table = Some(Project.TABLE)),
              BaseParameter(
                Challenge.FIELD_ENABLED,
                "",
                Operator.BOOL,
                table = Some(Challenge.TABLE)
              )
            ),
            includeWhere = false
          ),
          operator = Operator.CUSTOM
        ),
        BaseParameter(
          Challenge.FIELD_PARENT_ID,
          user.managedProjectIds,
          Operator.IN,
          table = Some(Challenge.TABLE)
        )
      ),
      OR(),
      !permission.isSuperUser(user)
    )
  }
}
