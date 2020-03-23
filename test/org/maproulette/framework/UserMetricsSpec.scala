/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework

import org.maproulette.framework.model.User
import org.maproulette.framework.service.{UserMetricService, UserService}
import org.maproulette.models.Task
import org.maproulette.utils.TestDatabase

/**
  * @author mcuthbert
  */
class UserMetricsSpec extends TestDatabase {
  val service: UserMetricService = this.serviceManager.userMetrics
  val userService: UserService   = this.serviceManager.user

  "UserMetricService" should {
    "get metrics for a user" in {
      //TODO
    }

    "updates the users score" in {
      val insertedUser =
        this.userService.create(this.getTestUser(19, "UpdateUserOSMService"), User.superUser)
      val updatedUser = this.service.updateUserScore(
        Some(Task.STATUS_FIXED),
        Some(1000),
        Some(1),
        true,
        true,
        Some(0),
        insertedUser.id
      )
    }
  }
}
