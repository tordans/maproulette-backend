/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.repository

import org.maproulette.framework.model.{Group, User}
import org.maproulette.framework.psql.Query
import org.maproulette.framework.psql.filter.BaseParameter
import org.maproulette.framework.service.GroupService
import org.maproulette.framework.util.{FrameworkHelper, GroupRepoTag, GroupTag}
import play.api.Application

/**
  * @author mcuthbert
  */
class GroupRepositorySpec(implicit val application: Application) extends FrameworkHelper {
  val repository: GroupRepository = this.application.injector.instanceOf(classOf[GroupRepository])
  val service: GroupService       = this.serviceManager.group

  var defaultGroup: Group = null
  var randomUser: User    = null

  "GroupRepository" should {
    "perform a basic query" taggedAs GroupRepoTag in {
      val groups = this.repository
        .query(Query.simple(List(BaseParameter(Group.FIELD_ID, this.defaultGroup.id))))
      groups.size mustEqual 1
      groups.head.id mustEqual this.defaultGroup.id
    }

    "retrieve a group" taggedAs GroupRepoTag in {
      val group = this.repository.retrieve(this.defaultGroup.id)
      group.get mustEqual this.defaultGroup
      val nonGroup = this.repository.retrieve(2345L)
      nonGroup.isEmpty mustEqual true
    }

    "create a group" taggedAs GroupRepoTag in {
      val createdGroup = this.repository
        .create(Group(-1, "RandomCreateGroup", this.defaultProject.id, Group.TYPE_ADMIN))
      val retrievedGroup = this.repository.retrieve(createdGroup.id)
      retrievedGroup.get mustEqual createdGroup
    }

    "update a group" taggedAs GroupRepoTag in {
      val randomGroup = this.repository
        .create(Group(-1, "RandomGroup", this.defaultProject.id, Group.TYPE_WRITE_ACCESS))
      // the project_id and type fields should be ignored, as you can only update the name
      this.repository.update(Group(randomGroup.id, "UpdatedName", 1234, Group.TYPE_READ_ONLY))
      val group = this.service.retrieve(randomGroup.id)
      group.get.name mustEqual "UpdatedName"
      group.get.projectId mustEqual randomGroup.projectId
      group.get.groupType mustEqual randomGroup.groupType
    }

    "delete a group" taggedAs GroupRepoTag in {
      val group =
        this.repository.create(Group(-1, "RANDOM_GROUP", this.defaultProject.id, Group.TYPE_ADMIN))
      this.repository.delete(group.id)
    }
  }

  override implicit val projectTestName: String = "GroupRepositorySpecProject"

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    defaultGroup = this.service.create(this.defaultProject.id, Group.TYPE_ADMIN, User.superUser)
    randomUser = this.serviceManager.user.create(
      this.getTestUser(12345, "RandomOUser"),
      User.superUser
    )
  }
}
