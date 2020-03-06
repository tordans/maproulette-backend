package org.maproulette.framework.service

import org.maproulette.Config
import org.maproulette.framework.model.{Group, User}
import org.maproulette.framework.repository.GroupRepository
import org.maproulette.permissions.Permission
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.Configuration

/**
  * @author mcuthbert
  */
class GroupServiceSpec extends PlaySpec with MockitoSugar {
  val groupRepository = mock[GroupRepository]
  val baseConfig = mock[Configuration]
  val permission = mock[Permission]
  when(baseConfig.getOptional(anyString())(any())).thenReturn(None)
  val config          = new Config()(baseConfig)
  val groupService    = new GroupService(groupRepository, config, permission)

  "GroupService" should {
    "not allow non super user to create groups" in {
      intercept[IllegalAccessException] {
        this.groupService.insert(1, 1, User.guestUser)
      }
    }

    "not allow non super user to update groups" in {
      intercept[IllegalAccessException] {
        this.groupService.update(1, "NewName", User.guestUser)
      }
    }

    "not allow non super user to delete groups" in {
      intercept[IllegalAccessException] {
        this.groupService.delete(1, User.guestUser)
      }
    }

    "not allow non super user to retrieve user groups" in {
      intercept[IllegalAccessException] {
        this.groupService.retrieveUserGroups(1, User.guestUser)
      }
    }

    "not allow non super user to retrieve project groups" in {
      intercept[IllegalAccessException] {
        this.groupService.retrieveProjectGroups(1, User.guestUser)
      }
    }

    "create a new group" in {
      when(this.groupRepository.insert(any[Group])(any()))
        .thenReturn(Group(1, "TestGroup", 1, Group.TYPE_ADMIN))
      val group = this.groupService.insert(1, Group.TYPE_ADMIN, User.superUser)
      group.id mustEqual 1
      group.projectId mustEqual 1
      group.name mustEqual "TestGroup"
      group.groupType mustEqual Group.TYPE_ADMIN
    }
  }
}
