/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package org.maproulette.framework.service

import org.maproulette.framework.model._
import org.maproulette.framework.psql.filter._
import org.maproulette.framework.psql.{Query, OR}

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
              BaseParameter("p.enabled", "", Operator.BOOL),
              BaseParameter("c.enabled", "", Operator.BOOL)
            ),
            includeWhere = false
          ),
          operator = Operator.CUSTOM
        ),
        BaseParameter("p.owner_id", user.osmProfile.id),
        SubQueryFilter(
          user.osmProfile.id.toString,
          Query.simple(
            List(
              BaseParameter("ug.group_id", "g.id", useValueDirectly = true),
              BaseParameter("g.project_id", "p.id", useValueDirectly = true)
            ),
            base = "SELECT ug.osm_user_id FROM user_groups ug, groups g "
          )
        )
      ),
      OR(),
      !user.isSuperUser
    )
  }
}
