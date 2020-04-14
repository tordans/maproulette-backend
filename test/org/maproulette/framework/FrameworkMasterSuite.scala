/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework

import org.maproulette.framework.repository._
import org.maproulette.framework.service._
import org.maproulette.framework.util.{TestDatabase, UserTag}
import org.scalatest.{BeforeAndAfterAll, Suite, Suites, Tag}

/**
  * @author mcuthbert
  */
class FrameworkMasterSuite extends Suites with BeforeAndAfterAll with TestDatabase {
  private val suites = IndexedSeq(
    new ChallengeServiceSpec,
    new ChallengeRepositorySpec,
    new CommentServiceSpec,
    new CommentRepositorySpec,
    new GroupServiceSpec,
    new GroupRepositorySpec,
    new ProjectServiceSpec,
    new ProjectRepositorySpec,
    new UserMetricsServiceSpec,
    new UserSavedObjectsServiceSpec,
    new UserSavedObjectsRepositorySpec,
    new UserServiceSpec,
    new UserRepositorySpec,
    new VirtualProjectServiceSpec,
    new VirtualProjectRepositorySpec,
    new TagServiceSpec,
    new TagRepositorySpec
  )
  private val tagFilters: Seq[Tag] = Seq()
  override val nestedSuites: IndexedSeq[Suite] =
    suites.filter(
      tagFilters.isEmpty || _.tags
        .flatMap(_._2)
        .exists(tag => tagFilters.map(_.name).contains(tag.toLowerCase))
    )

  override protected def beforeAll(): Unit = this.applyEvolutions()

  override protected def afterAll(): Unit = this.cleanupEvolutions()
}
