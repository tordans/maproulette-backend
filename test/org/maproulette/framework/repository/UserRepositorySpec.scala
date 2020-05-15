/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.repository

import java.util.UUID

import org.maproulette.framework.model.{User, UserMetrics, UserSettings}
import org.maproulette.framework.psql.Query
import org.maproulette.framework.psql.filter.{BaseParameter, Operator}
import org.maproulette.framework.service.UserService
import org.maproulette.framework.util.{FrameworkHelper, UserRepoTag, UserTag}
import play.api.Application
import play.api.libs.oauth.RequestToken

/**
  * @author mcuthbert
  */
class UserRepositorySpec(implicit val application: Application) extends FrameworkHelper {
  val userRepository: UserRepository = this.application.injector.instanceOf(classOf[UserRepository])
  val userService: UserService       = this.serviceManager.user

  "UserRepository" should {
    "upsert user" taggedAs UserRepoTag in {
      val insertedUser  = this.insertBaseUser(1, "name1")
      val retrievedUser = this.repositoryGet(insertedUser.id)
      retrievedUser.get mustEqual insertedUser
    }

    "update user" taggedAs UserRepoTag in {
      val insertedUser  = this.insertBaseUser(2, "name2")
      val updatedApiKey = UUID.randomUUID().toString
      val updateUser = insertedUser.copy(
        osmProfile = insertedUser.osmProfile.copy(
          displayName = "name3",
          avatarURL = "UPDATE_avatarURL",
          requestToken = RequestToken("UPDATED_TOKEN", "UPDATED_SECRET")
        ),
        apiKey = Some(updatedApiKey),
        settings = UserSettings(
          Some(1),
          Some(2),
          Some("id"),
          Some("basemap"),
          Some("en-US"),
          Some("email_address"),
          Some(true),
          Some(false),
          Some(5),
          Some(true)
        )
      )
      this.userRepository.update(updateUser, "POINT (14.0 22.0)")
      val updatedUser = this.repositoryGet(insertedUser.id).get
      updatedUser.osmProfile.displayName mustEqual updateUser.osmProfile.displayName
      updatedUser.osmProfile.avatarURL mustEqual updateUser.osmProfile.avatarURL
      updatedUser.osmProfile.requestToken mustEqual updateUser.osmProfile.requestToken
      // API Key should not be allowed to updated here
      updatedUser.apiKey mustEqual insertedUser.apiKey
      updatedUser.settings mustEqual updateUser.settings
    }

    "update API key" taggedAs UserRepoTag in {
      val insertedUser =
        this.userRepository.upsert(this.getTestUser(3, "APITest"), "TestAPIKey", "POINT (20 40)")
      this.userRepository.updateAPIKey(insertedUser.id, "NEW_updated_key")
      val retrievedUser = this.repositoryGet(insertedUser.id)
      retrievedUser.get.apiKey.get mustEqual "NEW_updated_key"
    }

    "delete user" taggedAs UserRepoTag in {
      val insertedUser = this.userRepository
        .upsert(this.getTestUser(4, "DeleteTest"), "TestAPIKey", "POINT (20 40)")
      this.userRepository.delete(insertedUser.id)
      val retrievedUser = this.repositoryGet(insertedUser.id)
      retrievedUser.isEmpty mustEqual true
    }

    "delete user by OSMID" taggedAs UserRepoTag in {
      val insertedUser = this.userRepository
        .upsert(this.getTestUser(5, "DeleteByOidTest"), "TestAPIKey", "POINT (20 40)")
      this.userRepository.deleteByOSMID(5)
      val retrievedUser = this.repositoryGet(insertedUser.id)
      retrievedUser.isEmpty mustEqual true
    }

    "update user score" taggedAs UserRepoTag in {
      val insertedUser = this.userRepository
        .upsert(this.getTestUser(61, "UpdateUserO"), "TestAPIKey", "POINT (20 40)")
      val updatedUser = this.userRepository.updateUserScore(
        insertedUser.id,
        List(
          BaseParameter(
            UserMetrics.FIELD_SCORE,
            s"=(${UserMetrics.FIELD_SCORE}+1000)",
            Operator.CUSTOM
          ),
          BaseParameter(
            UserMetrics.FIELD_TOTAL_REJECTED,
            s"=(${UserMetrics.FIELD_TOTAL_REJECTED}+1)",
            Operator.CUSTOM
          )
        )
      )
    }
  }

  override implicit val projectTestName: String = "UserRepositorySpecProject"

  private def repositoryGet(id: Long): Option[User] = {
    this.userRepository
      .query(
        Query.simple(
          List(BaseParameter(User.FIELD_ID, id))
        )
      )
      .headOption
  }

  private def insertBaseUser(osmId: Long, osmName: String): User =
    this.userService.create(this.getTestUser(osmId, osmName), User.superUser)
}
