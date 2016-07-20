// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.controllers.api

import javax.inject.Inject

import io.swagger.annotations._
import org.apache.commons.lang3.StringUtils
import org.maproulette.actions.{ActionManager, ProjectType, TaskViewed}
import org.maproulette.controllers.ParentController
import org.maproulette.models.dal.{ProjectDAL, TaskDAL}
import org.maproulette.models.{Challenge, ClusteredPoint, Project, Task}
import org.maproulette.session.{SearchParameters, SessionManager, User}
import org.maproulette.utils.Utils
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent}

/**
  * The project controller handles all operations for the Project objects.
  * This includes CRUD operations and searching/listing.
  * See {@link org.maproulette.controllers.ParentController} for more details on parent object operations
  * See {@link org.maproulette.controllers.CRUDController} for more details on CRUD object operations
  *
  * @author cuthbertm
  */
@Api(value = "/Project", description = "Operations for Projects", protocols = "http")
class ProjectController @Inject() (override val childController:ChallengeController,
                                   override val sessionManager:SessionManager,
                                   override val actionManager: ActionManager,
                                   override val dal:ProjectDAL,
                                   taskDAL: TaskDAL)
  extends ParentController[Project, Challenge] {

  // json reads for automatically reading Projects from a posted json body
  override implicit val tReads: Reads[Project] = Project.projectReads
  // json writes for automatically writing Projects to a json body response
  override implicit val tWrites: Writes[Project] = Project.projectWrites
  // json writes for automatically writing Challenges to a json body response
  override protected val cWrites: Writes[Challenge] = Challenge.challengeWrites
  // json reads for automatically reading Challenges from a posted json body
  override protected val cReads: Reads[Challenge] = Challenge.challengeReads
  // The type of object that this controller deals with.
  override implicit val itemType = ProjectType()

  implicit val writes = ClusteredPoint.clusteredPointWrites

  /**
    * This function allows sub classes to modify the body, primarily this would be used for inserting
    * default elements into the body that shouldn't have to be required to create an object.
    *
    * @param body The incoming body from the request
    * @return
    */
  override def updateCreateBody(body: JsValue, user:User): JsValue = {
    var jsonBody = super.updateCreateBody(body, user)
    jsonBody = Utils.insertIntoJson(jsonBody, "groups", Array.emptyShortArray)(arrayWrites[Short])
    Utils.insertIntoJson(jsonBody, "enabled", true)(BooleanWrites)
  }

  /**
    * We override the base function and force -1 as the parent, as projects do not have parents.
    */
  // scalastyle:off
  @ApiOperation(
    nickname = "readByName",
    value = "Get a project based on it's name.",
    notes =
      """This method will retrieve a project based on the supplied name.""",
    httpMethod = "GET",
    produces = "application/json",
    protocols = "http",
    code = 200,
    response = classOf[Project]
  )
  // scalastyle:on
  override def readByName(
                           @ApiParam(value="This value is ignored") id: Long,
                           @ApiParam(value="Name of the project.") name: String
                         ): Action[AnyContent] = super.readByName(-1, name)

  /**
    * Gets a random task that is an descendant of the project.
    *
    * @param projectId The project id, ie. the ancestor of the child.
    * @param challengeSearch Filter based on the name of the challenge
    * @param challengeTags Filter based on the tags of the challenge
    * @param tags A comma separated list of tags that optionally can be used to further filter the tasks
    * @param taskSearch Filter based on the name of the task
    * @param limit Limit of how many tasks should be returned
    * @return A list of Tasks that match the supplied filters
    */
  // scalastyle:off
  @ApiOperation(
    nickname = "RandomTasks",
    value = "Get a random tasks within a project.",
    notes =
      """This method will retrieve random task(s) from all the tasks contained within the challenges of the provided project.""",
    httpMethod = "GET",
    produces = "application/json",
    protocols = "http",
    code = 200,
    response = classOf[Task],
    responseContainer = "List"
  )
  // scalastyle:on
  def getRandomTasks(
                      @ApiParam(value="ID of the project.") projectId: Long,
                      @ApiParam(value="Search text to filter by the name of the challenge.") challengeSearch:String,
                      @ApiParam(value="Comma separated list of tags to filter the challenges by.") challengeTags:String,
                      @ApiParam(value="Comma separated list of tags to filter the tasks by.") tags: String,
                      @ApiParam(value="Search text to filter by the name of the tasks.") taskSearch:String,
                      @ApiParam(value="Limit the number of returned tasks.") limit:Int
                    ) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      val params = SearchParameters(
        projectId = Some(projectId),
        challengeSearch = challengeSearch,
        challengeTags = challengeTags.split(",").toList,
        taskSearch = taskSearch,
        taskTags = tags.split(",").toList
      )
      val result = this.taskDAL.getRandomTasks(User.userOrMocked(user), params, limit)
      result.foreach(task => this.actionManager.setAction(user, this.itemType.convertToItem(task.id), TaskViewed(), ""))
      Ok(Json.toJson(result))
    }
  }

  // scalastyle:off
  @ApiOperation(
    nickname = "SearchedClusteredPoints",
    value = "Retrieve a list of a clustered points.",
    notes =
      """
        This method will retrieve a list of clustered points, that is filtered by the search cookie string. The search cookie string is a url
        encoded json object that has the following properties: projectId, projectSearch, projectEnabled, challengeId, challengeTags,
        challengeSearch, challengeEnabled, taskTags, taskSearch, priority, location.
      """,
    httpMethod = "GET",
    produces = "application/json",
    protocols = "http",
    code = 200,
    response = classOf[ClusteredPoint],
    responseContainer = "List"
  )
  // scalastyle:on
  def getSearchedClusteredPoints(
                                  @ApiParam(value="URL Encoded search cookie json object.") searchCookie:String
                                ) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      val searchParams = SearchParameters.convert(searchCookie)
      Ok(Json.toJson(this.dal.getSearchedClusteredPoints(searchParams)))
    }
  }

  // scalastyle:off
  @ApiOperation(
    nickname = "ClusteredPoints",
    value = "Retrieve a list of a clustered points.",
    notes =
      """This method will retrieve a list of clustered points for a set of challenges or for a project.""",
    httpMethod = "GET",
    produces = "application/json",
    protocols = "http",
    code = 200,
    response = classOf[ClusteredPoint],
    responseContainer = "List"
  )
  // scalastyle:on
  def getClusteredPoints(
                          @ApiParam(value="A single project id. If negative will assume all projects") projectId:Long,
                          @ApiParam(value="A comma separated list of challenge ids to filter the list by") challengeIds:String
                        ) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      val pid = if (projectId < 0) {
        None
      } else {
        Some(projectId)
      }
      val cids = if (StringUtils.isEmpty(challengeIds)) {
        List.empty
      } else {
        challengeIds.split(",").map(_.toLong).toList
      }
      Ok(Json.toJson(this.dal.getClusteredPoints(pid, cids)))
    }
  }
}
