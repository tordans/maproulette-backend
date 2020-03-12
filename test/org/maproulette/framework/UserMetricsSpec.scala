/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework

import org.maproulette.framework.service.UserMetricService
import org.maproulette.utils.TestDatabase

/**
 * @author mcuthbert
 */
class UserMetricsSpec extends TestDatabase {
  val service:UserMetricService = this.application.injector.instanceOf(classOf[UserMetricService])

  "UserMetricService" should {
    "get metrics for a user" in {
      //TODO
    }

    "updates the users score" in {
      //TODO
    }
  }
}
