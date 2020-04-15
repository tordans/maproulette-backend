/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.service

import org.maproulette.framework.model.{Group, Project, User}
import org.maproulette.framework.util.{FrameworkHelper, GroupTag}
import play.api.Application

/**
  * @author mcuthbert
  */
class GroupServiceSpec(implicit val application: Application) extends FrameworkHelper {
  val service: GroupService = this.serviceManager.group

  var defaultGroup: Group = null
  var randomUser: User    = null

  "GroupService" should {
    "not allow non super user to create groups" taggedAs GroupTag in {
      intercept[IllegalAccessException] {
        this.service.create(1, 1, User.guestUser)
      }
    }

    "not allow non super user to update groups" taggedAs GroupTag in {
      intercept[IllegalAccessException] {
        this.service.update(1, "NewName", User.guestUser)
      }
    }

    "not allow non super user to delete groups" taggedAs GroupTag in {
      intercept[IllegalAccessException] {
        this.service.delete(1, User.guestUser)
      }
    }

    "not allow non super user to retrieve user groups" taggedAs GroupTag in {
      intercept[IllegalAccessException] {
        this.service.retrieveUserGroups(1, User.guestUser)
      }
    }

    "not allow non super user to retrieve project groups" taggedAs GroupTag in {
      intercept[IllegalAccessException] {
        this.service.retrieveProjectGroups(1, User.guestUser)
      }
    }

    "create a new group" taggedAs GroupTag in {
      val group = this.service.create(this.defaultProject.id, Group.TYPE_ADMIN, User.superUser)
      group.projectId mustEqual this.defaultProject.id
      group.groupType mustEqual Group.TYPE_ADMIN
      group.name mustEqual s"${this.defaultProject.id}_Admin"
      val retrievedGroup = this.service.retrieve(group.id)
      retrievedGroup.get mustEqual group
    }

    "update a group" taggedAs GroupTag in {
      val group = this.service.create(this.defaultProject.id, Group.TYPE_READ_ONLY, User.superUser)
      this.service.update(group.id, "UpdatedNewNameForGroup", User.superUser)
      val retrievedGroup = this.service.retrieve(group.id)
      retrievedGroup.get.id mustEqual group.id
      retrievedGroup.get.name mustEqual "UpdatedNewNameForGroup"
    }

    "handle user groups for OSM user" taggedAs GroupTag in {
      this.service.addUserToGroup(this.randomUser.osmProfile.id, defaultGroup, User.superUser)
      val groups = this.service.retrieveUserGroups(this.randomUser.osmProfile.id, User.superUser)
      groups.size mustEqual 1
      groups.head mustEqual defaultGroup

      this.service.removeUserFromGroup(this.randomUser.osmProfile.id, defaultGroup, User.superUser)
      val groups2 = this.service.retrieveUserGroups(this.randomUser.osmProfile.id, User.superUser)
      groups2.size mustEqual 0
    }

    "handle user project groups based on OSM id" taggedAs GroupTag in {
      val project = this.serviceManager.project
        .create(Project(-1, User.superUser.id, "GroupTestingProject2"), User.superUser)
      val projectReadGroup = project.groups.filter(_.groupType == Group.TYPE_READ_ONLY).head
      // add the user all all the project groups
      this.service
        .addUserToProject(
          this.randomUser.osmProfile.id,
          Group.TYPE_READ_ONLY,
          project.id,
          User.superUser
        )
      val groups = this.service.retrieveUserGroups(this.randomUser.osmProfile.id, User.superUser)
      groups.size mustEqual 1
      groups.head mustEqual projectReadGroup

      // don't remove user from a group it doesn't exist in
      this.service.removeUserFromProjectGroups(
        this.randomUser.osmProfile.id,
        project.id,
        Group.TYPE_WRITE_ACCESS,
        User.superUser
      )
      val groups2 =
        this.service.retrieveUserGroups(this.randomUser.osmProfile.id, User.superUser)
      groups2.size mustEqual 1

      this.service.removeUserFromProjectGroups(
        this.randomUser.osmProfile.id,
        project.id,
        Group.TYPE_READ_ONLY,
        User.superUser
      )
      val groups3 =
        this.service.retrieveUserGroups(this.randomUser.osmProfile.id, User.superUser)
      groups3.size mustEqual 0
    }

    "retrieve project groups" taggedAs GroupTag in {
      val project = this.serviceManager.project
        .create(Project(-1, User.superUser.id, "GroupTestingProject3"), User.superUser)
      val projectGroups = this.service.retrieveProjectGroups(project.id, User.superUser)
      projectGroups.size mustEqual 3
      this.service.create(project.id, Group.TYPE_ADMIN, User.superUser, "NewAdminGroup")
      val groups = this.service.retrieveProjectGroups(project.id, User.superUser)
      groups.size mustEqual 4
    }
  }

  override implicit val projectTestName: String = "GroupServiceSpecProject"

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    defaultGroup = this.service.create(this.defaultProject.id, Group.TYPE_ADMIN, User.superUser)
    randomUser = this.serviceManager.user.create(
      this.getTestUser(12345, "RandomOUser"),
      User.superUser
    )
  }
}
