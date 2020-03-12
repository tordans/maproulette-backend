/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.utils

import org.joda.time.DateTime
import org.maproulette.framework.model._
import org.maproulette.framework.psql.Query
import org.maproulette.framework.psql.filter.BaseParameter
import org.maproulette.framework.repository.ProjectRepository
import org.maproulette.framework.service.ServiceManager
import org.maproulette.models.Task
import org.maproulette.models.dal.{ChallengeDAL, TaskDAL}
import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.db.Database
import play.api.db.evolutions.Evolutions
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.oauth.RequestToken

/**
  * A little helper class to create and drop test tables
  *
  * @author mcuthbert
  */
trait TestDatabase extends PlaySpec with BeforeAndAfterAll with MockitoSugar {
  val application = GuiceApplicationBuilder()
    .configure(
      "db.default.url"      -> "jdbc:postgresql://localhost:5433/mr_test",
      "db.default.username" -> "osm",
      "db.default.password" -> "osm",
      "db.default.logSql"   -> false
    )
    .build()

  implicit val database = this.application.injector.instanceOf(classOf[Database])

  val serviceManager: ServiceManager = this.application.injector.instanceOf(classOf[ServiceManager])
  val challengeDAL: ChallengeDAL     = this.application.injector.instanceOf(classOf[ChallengeDAL])
  val taskDAL: TaskDAL               = this.application.injector.instanceOf(classOf[TaskDAL])

  val DEFAULT_CHALLENGE_NAME = "TestChallenge"
  val DEFAULT_TASK_NAME      = "TestTask"

  var defaultProject: Project     = null
  var defaultChallenge: Challenge = null
  var defaultTask: Task           = null

  override protected def beforeAll(): Unit = {
    Evolutions.applyEvolutions(database)
    this.insertTestData()
  }

  private def insertTestData(): Unit = {
    defaultProject = this.serviceManager.project
      .create(Project(-1, User.superUser.osmProfile.id, "TestProject"), User.superUser)
    val challenge = challengeDAL.insert(
      this.getDummyChallenge("TestChallenge"),
      User.superUser
    )
    defaultChallenge = this.serviceManager.challenge
      .query(Query.simple(List(BaseParameter(Challenge.FIELD_ID, challenge.id))))
      .head
    val task = taskDAL.insert(
      this.getDummyTask(DEFAULT_TASK_NAME),
      User.superUser
    )
    // TODO there is currently a bug in the TaskDAL that after insert will insert the incorrect version of the Task into the cache. This will need to be fixed.
    this.taskDAL.cacheManager.clearCaches
    defaultTask = this.taskDAL.retrieveById(task.id).head
  }

  protected def getDummyChallenge(name: String, parentId:Long = defaultProject.id): Challenge = {
    Challenge(
      -1,
      name,
      null,
      null,
      general = ChallengeGeneral(
        User.superUser.osmProfile.id,
        parentId,
        "TestChallengeInstruction"
      ),
      creation = ChallengeCreation(),
      priority = ChallengePriority(),
      extra = ChallengeExtra()
    )
  }

  protected def getDummyTask(name: String, parentId:Long = defaultChallenge.id): Task = {
    Task(
      -1,
      name,
      null,
      null,
      parentId,
      geometries =
        "{\"features\":[{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[-60.811801,-32.9199812],[-60.8117804,-32.9199856],[-60.8117816,-32.9199896],[-60.8117873,-32.919984]]},\"properties\":{\"osm_id\":\"OSM_W_378169283_000000_000\",\"pbfHistory\":[\"20200110-043000\"]}}]}"
    )
  }

  protected def getDummyUser(osmId: Long, osmName: String): User = {
    User(
      -1,
      null,
      null,
      OSMProfile(
        osmId,
        osmName,
        "Test User",
        "",
        Location(1.0, 2.0),
        DateTime.now(),
        RequestToken("token", "secret")
      ),
      List.empty,
      Some("apiKeyTEst")
    )
  }

  override protected def afterAll(): Unit = {
    Evolutions.cleanupEvolutions(database)
  }
}
