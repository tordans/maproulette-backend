/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework

import org.maproulette.exception.{InvalidException, NotFoundException}
import org.maproulette.framework.model.{Project, User}
import org.maproulette.framework.repository.VirtualProjectRepository
import org.maproulette.framework.service.VirtualProjectService
import org.maproulette.utils.TestDatabase

/**
  * @author mcuthbert
  */
class VirtualProjectSpec extends TestDatabase {
  val repository: VirtualProjectRepository =
    this.application.injector.instanceOf(classOf[VirtualProjectRepository])
  val service: VirtualProjectService = this.serviceManager.virtualProject

  var virtualProject: Project = this.serviceManager.project
    .create(
      Project(-1, User.superUser.id, "VirtualProject", isVirtual = Some(true)),
      User.superUser
    )

  "VirtualProjectRepository" should {
    "add challenges to virtual project" in {
      val challenge =
        this.challengeDAL
          .insert(this.getTestChallenge("VP_Challenge"), User.superUser)

      this.repository.addChallenge(this.virtualProject.id, challenge.id)
      val challenges = this.service.listVirtualChildren(this.virtualProject.id, User.superUser)
      challenges.size mustEqual 1
      challenges.head.id mustEqual challenge.id

      this.repository.removeChallenge(this.virtualProject.id, challenge.id)
      val challenges2 = this.service.listVirtualChildren(this.virtualProject.id, User.superUser)
      challenges2.size mustEqual 0

      // cleanup
      this.challengeDAL.delete(challenge.id, User.superUser, true)
    }
  }

  "VirtualService" should {
    "add challenges to virtual project" in {
      val challenge =
        this.challengeDAL.insert(this.getTestChallenge("VP_Challenge"), User.superUser)

      this.service.addChallenge(this.virtualProject.id, challenge.id, User.superUser)
      val challenges = this.service.listVirtualChildren(this.virtualProject.id, User.superUser)
      challenges.size mustEqual 1
      challenges.head.id mustEqual challenge.id

      this.service.removeChallenge(this.virtualProject.id, challenge.id, User.superUser)
      val challenges2 = this.service.listVirtualChildren(this.virtualProject.id, User.superUser)
      challenges2.size mustEqual 0

      // cleanup
      this.challengeDAL.delete(challenge.id, User.superUser, true)
    }

    "not add challenges to non virtual project" in {
      val project = this.serviceManager.project
        .create(Project(-1, User.superUser.id, "VirtualProject2Test"), User.superUser)
      val challenge =
        this.challengeDAL
          .insert(this.getTestChallenge("VP_Challenge"), User.superUser)
      intercept[InvalidException] {
        this.service.addChallenge(project.id, challenge.id, User.superUser)
      }
      // cleanup
      this.serviceManager.project.delete(project.id, User.superUser, true)
    }

    "throw NotFoundException if project not found when adding challenge" in {
      intercept[NotFoundException] {
        this.service.addChallenge(12354, this.defaultChallenge.id, User.superUser)
      }
    }

    "throw NotFoundException if project not found when removing challenge" in {
      intercept[NotFoundException] {
        this.service.removeChallenge(12345, this.defaultChallenge.id, User.superUser)
      }
    }

    "not remove challenges from non virtual project" in {
      intercept[InvalidException] {
        this.service
          .removeChallenge(this.defaultProject.id, this.defaultChallenge.id, User.superUser)
      }
    }
  }
}
