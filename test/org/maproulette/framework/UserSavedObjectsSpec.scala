package org.maproulette.framework

import org.maproulette.framework.model.User
import org.maproulette.framework.repository.UserSavedObjectsRepository
import org.maproulette.framework.service.UserService
import org.maproulette.utils.TestDatabase

/**
  * @author mcuthbert
  */
class UserSavedObjectsSpec extends TestDatabase {
  val repository: UserSavedObjectsRepository =
    this.application.injector.instanceOf(classOf[UserSavedObjectsRepository])
  val userService: UserService = this.serviceManager.user
  val insertedUser =
    this.userService.create(this.getDummyUser(1, "SaveChallengeOSMUser"), User.superUser)

  "UserSavedObjectsRepository" should {
    "save challenge(s) to user profile" in {
      this.repository.saveChallenge(this.insertedUser.id, this.defaultChallenge.id)
      val savedChallenges = this.repository.getSavedChallenges(this.insertedUser.id)
      savedChallenges.size mustEqual 1
      savedChallenges.head.id mustEqual this.defaultChallenge.id

      this.repository.unsaveChallenge(this.insertedUser.id, this.defaultChallenge.id)
      val savedChallenges2 = this.repository.getSavedChallenges(this.insertedUser.id)
      savedChallenges2.isEmpty mustEqual true
    }

    "save task(s) to user profile" in {
      this.repository.saveTask(this.insertedUser.id, this.defaultTask.id, this.defaultTask.parent)
      val savedTasks = this.repository.getSavedTasks(this.insertedUser.id)
      savedTasks.size mustEqual 1
      savedTasks.head.id mustEqual this.defaultTask.id

      this.repository.unsaveTask(this.insertedUser.id, this.defaultTask.id)
      val savedTasks2 = this.repository.getSavedTasks(this.insertedUser.id)
      savedTasks2.isEmpty mustEqual true
    }

    "save task(s) to user profile for specific challenges" in {
      this.repository.saveTask(this.insertedUser.id, this.defaultTask.id, this.defaultTask.parent)
      // create a new challenge and task
      val challenge =
        this.challengeDAL.insert(this.getDummyChallenge("DUMMY_CHALLENGE_2"), User.superUser)
      val task = this.taskDAL
        .insert(this.getDummyTask("DUMMY_TASK_2").copy(parent = challenge.id), User.superUser)
      this.repository.saveTask(this.insertedUser.id, task.id, task.parent)

      val allTasks = this.repository.getSavedTasks(this.insertedUser.id)
      allTasks.size mustEqual 2

      val challenge1Tasks =
        this.repository.getSavedTasks(this.insertedUser.id, List(this.defaultChallenge.id))
      challenge1Tasks.size mustEqual 1
      challenge1Tasks.head.id mustEqual this.defaultTask.id
      val challenge2Tasks = this.repository.getSavedTasks(this.insertedUser.id, List(challenge.id))
      challenge2Tasks.size mustEqual 1
      challenge2Tasks.head.id mustEqual task.id

      // clean up - if this doesn't clean up correctly tests in the "UserService" section will fail
      this.repository.unsaveTask(this.insertedUser.id, this.defaultTask.id)
      this.repository.unsaveTask(this.insertedUser.id, task.id)
    }
  }

  "UserService" should {
    "save challenge(s) in user profile" in {
      this.userService.saveChallenge(this.insertedUser.id, this.defaultChallenge.id, User.superUser)
      val challenges = this.userService.getSavedChallenges(this.insertedUser.id, User.superUser)
      challenges.size mustEqual 1
      challenges.head.id mustEqual this.defaultChallenge.id

      this.userService
        .unsaveChallenge(this.insertedUser.id, this.defaultChallenge.id, User.superUser)
      val challenges2 = this.userService.getSavedChallenges(this.insertedUser.id, User.superUser)
      challenges2.size mustEqual 0
    }

    "save task(s) in user profile" in {
      this.userService.saveTask(this.insertedUser.id, this.defaultTask.id, User.superUser)
      val tasks = this.userService.getSavedTasks(this.insertedUser.id, User.superUser)
      tasks.size mustEqual 1
      tasks.head.id mustEqual this.defaultTask.id

      this.userService.unsaveTask(this.insertedUser.id, this.defaultTask.id, User.superUser)
      val tasks2 = this.userService.getSavedTasks(this.insertedUser.id, User.superUser)
      tasks2.size mustEqual 0
    }

    "save task(s) to user profile for specific challenges" in {
      // create a new challenge and task
      val challenge =
        this.challengeDAL.insert(this.getDummyChallenge("DUMMY_CHALLENGE_3"), User.superUser)
      val task = this.taskDAL
        .insert(this.getDummyTask("DUMMY_TASK_3").copy(parent = challenge.id), User.superUser)
      this.userService.saveTask(this.insertedUser.id, this.defaultTask.id, User.superUser)
      this.userService.saveTask(this.insertedUser.id, task.id, User.superUser)

      val tasks = this.userService.getSavedTasks(this.insertedUser.id, User.superUser)
      tasks.size mustEqual 2

      val challenge1Tasks = this.userService
        .getSavedTasks(this.insertedUser.id, User.superUser, List(this.defaultChallenge.id))
      challenge1Tasks.size mustEqual 1
      challenge1Tasks.head.id mustEqual this.defaultTask.id

      val challenge2Tasks =
        this.userService.getSavedTasks(this.insertedUser.id, User.superUser, List(challenge.id))
      challenge2Tasks.size mustEqual 1
      challenge2Tasks.head.id mustEqual task.id
    }
  }
}
