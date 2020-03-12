/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework

import org.joda.time.{DateTime, Months}
import org.maproulette.framework.model.{Group, Project, User}
import org.maproulette.framework.psql.Query
import org.maproulette.framework.psql.filter.BaseParameter
import org.maproulette.framework.repository.ProjectRepository
import org.maproulette.framework.service.ProjectService
import org.maproulette.session.{SearchChallengeParameters, SearchLocation, SearchParameters}
import org.maproulette.utils.TestDatabase

/**
  * @author mcuthbert
  */
class ProjectSpec extends TestDatabase {
  val repository: ProjectRepository =
    this.application.injector.instanceOf(classOf[ProjectRepository])
  val service: ProjectService = this.application.injector.instanceOf(classOf[ProjectService])

  "ProjectRepository" should {
    "perform a basic query correctly" in {
      val project = this.repository
        .query(Query.simple(List(BaseParameter(Project.FIELD_ID, this.defaultProject.id))))
      project.head mustEqual this.defaultProject
    }

    "create a new project" in {
      val createdProject =
        this.repository.create(Project(-1, User.superUser.osmProfile.id, "CreateProjectTest"))
      val retrievedProject = this.repository.retrieve(createdProject.id)
      retrievedProject.get mustEqual createdProject
    }

    "retrieve a project" in {
      val retrievedProject = this.repository.retrieve(this.defaultProject.id)
      retrievedProject.get mustEqual this.defaultProject
    }

    "update a project" in {
      val newCreatedProject =
        this.repository.create(Project(-1, User.superUser.osmProfile.id, "UpdateProjectTest"))
      val retrievedProject = this.repository.retrieve(newCreatedProject.id)
      retrievedProject.get mustEqual newCreatedProject

      // create a new user for the ownership of the project
      val randomUser = this.serviceManager.user
        .create(this.getDummyUser(456677, "DummyProjectUser"), User.superUser)
      val randomDateTime = DateTime.now.minus(Months.ONE)
      // update everything, including things actually not expected to be updated like created, modified, groups and deleted
      val updatedProject = Project(
        newCreatedProject.id,
        randomUser.osmProfile.id,
        "UPDATE_NAME",
        description = Some("UPDATE_DESCRIPTION"),
        displayName = Some("UPDATE_DISPLAYNAME"),
        enabled = true,
        isVirtual = Some(true),
        featured = true,
        deleted = true,
        created = randomDateTime,
        modified = randomDateTime,
        groups = List(Group(-1, "RANDOM", newCreatedProject.id, Group.TYPE_ADMIN))
      )
      this.repository.update(updatedProject)
      val dbUpdatedProject = this.repository.retrieve(newCreatedProject.id)
      dbUpdatedProject.get.id mustEqual newCreatedProject.id
      dbUpdatedProject.get.owner mustEqual randomUser.osmProfile.id
      dbUpdatedProject.get.name mustEqual "UPDATE_NAME"
      dbUpdatedProject.get.description mustEqual Some("UPDATE_DESCRIPTION")
      dbUpdatedProject.get.displayName mustEqual Some("UPDATE_DISPLAYNAME")
      dbUpdatedProject.get.enabled mustEqual true
      dbUpdatedProject.get.isVirtual mustEqual Some(true)
      dbUpdatedProject.get.featured mustEqual true
      dbUpdatedProject.get.deleted mustEqual false
      dbUpdatedProject.get.created mustEqual newCreatedProject.created
      dbUpdatedProject.get.modified.isAfter(randomDateTime)
      dbUpdatedProject.get.groups.size mustEqual 0
    }

    "delete a project" in {
      val newCreatedProject =
        this.repository.create(Project(-1, User.superUser.osmProfile.id, "DeleteProjectTest"))
      val retrievedProject = this.repository.retrieve(newCreatedProject.id)
      retrievedProject.get mustEqual newCreatedProject
      this.repository.delete(newCreatedProject.id, false)
      val deletedProject = this.repository.retrieve(newCreatedProject.id)
      // it should still exist in the database because only the flag has been set
      deletedProject.get.deleted mustEqual true
      this.repository.delete(newCreatedProject.id, true)
      val reallyDeletedProject = this.repository.retrieve(newCreatedProject.id)
      reallyDeletedProject mustEqual None
    }

    "get searched clustered points" in {
      // this is really just testing the validation of the process, as there is only 1 task as part of the test data
      val clusteredPoints = this.repository.getSearchedClusteredPoints(
        SearchParameters(
          challengeParams = SearchChallengeParameters(
            challengeTags = Some(List("test")),
            challengeSearch = Some("test"),
            challengeEnabled = Some(true)
          ),
          projectSearch = Some("test"),
          projectEnabled = Some(true),
          projectIds = Some(List(this.defaultProject.id)),
          location = Some(SearchLocation(1.5, 2.5, 3.5, 4.5))
        )
      )
      clusteredPoints.isEmpty mustEqual true
    }

    "get clustered points" in {
      // this is really just testing the validation of the process, as there is only 1 task as part of the test data
      val clusteredPoints = this.repository
        .getClusteredPoints(Some(this.defaultProject.id), List(this.defaultChallenge.id), true)
      clusteredPoints.isEmpty mustEqual true
    }
  }

  "ProjectService" should {
    "get all the children of a project" in {
      val challenges = this.service.children(this.defaultProject.id)
      challenges.size mustEqual 1
      challenges.head.id mustEqual this.defaultChallenge.id
    }

    "create a new project" in {
      val createdProject = this.service.create(Project(-1, User.superUser.osmProfile.id, "ServiceCreateProject"), User.superUser)
      val retrievedProject = this.service.retrieve(createdProject.id)
      retrievedProject.get mustEqual createdProject
      // make sure that the groups were created as well for the project
      val createdGroups = retrievedProject.get.groups
      createdGroups.size mustEqual 3
    }

    "get the projects managed by a specific user" in {

    }
  }
}
