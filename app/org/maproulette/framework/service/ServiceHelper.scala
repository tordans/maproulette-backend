/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package org.maproulette.framework.service

import org.maproulette.framework.model._
import org.maproulette.framework.psql.filter._
import org.maproulette.framework.psql.{OR, Query}

/**
  * Helper functions for services
  *
  */
trait ServiceHelper {

  /**
    * Returns a FilterGroup that creates a subquery to filter challengs
    * to include only ones visible to the user. Which includes:
    *  - Both Project and Challenge are enabled
    *  - Project is owned by the user
    *  - User belongs to a user_group associated with the project
    *  - Or user is a superUser
    *
    * @param - User to check visibility for.
    */
  def challengeVisibilityFilter(user: User): FilterGroup = {
    // If !superUser
    // s""" AND ((p.enabled AND c.enabled) OR
    //       p.owner_id = ${user.osmProfile.id} OR
    //       ${user.osmProfile.id} IN (SELECT ug.osm_user_id FROM user_groups ug, groups g
    //                                  WHERE ug.group_id = g.id AND g.project_id = p.id))
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
        BaseParameter(Project.FIELD_OWNER, user.osmProfile.id, table = Some(Project.TABLE)),
        SubQueryFilter(
          user.osmProfile.id.toString,
          Query.simple(
            List(
              BaseParameter(
                Group.FIELD_UG_GROUP_ID,
                "groups.id",
                useValueDirectly = true,
                table = Some(Group.TABLE_USER_GROUP)
              ),
              BaseParameter(
                Group.FIELD_PROJECT_ID,
                "projects.id",
                useValueDirectly = true,
                table = Some(Group.TABLE)
              )
            ),
            base = "SELECT ug.osm_user_id FROM user_groups, groups "
          ),
          table = Some("")
        )
      ),
      OR(),
      !user.isSuperUser
    )
  }
}
