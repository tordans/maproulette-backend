// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.controllers.api

import java.sql.Connection
import javax.inject.Inject

import io.swagger.annotations._
import org.apache.commons.lang3.StringUtils
import org.maproulette.actions.{ActionManager, Actions, ChallengeType, TaskViewed}
import org.maproulette.controllers.ParentController
import org.maproulette.exception.{NotFoundException, StatusMessage}
import org.maproulette.models.dal._
import org.maproulette.models.{Challenge, ClusteredPoint, Survey, Task}
import org.maproulette.session.dal.UserDAL
import org.maproulette.session.{SearchParameters, SessionManager, User}
import org.maproulette.utils.Utils
import play.api.libs.json.{JsValue, Json, Reads, Writes}
import play.api.mvc.{Action, AnyContent}

/**
  * The challenge controller handles all operations for the Challenge objects.
  * This includes CRUD operations and searching/listing.
  * See {@link org.maproulette.controllers.ParentController} for more details on parent object operations
  * See {@link org.maproulette.controllers.CRUDController} for more details on CRUD object operations
  *
  * @author cuthbertm
  */
@Api(value = "/Challenge", description = "Operations for Challenges", protocols = "http")
class ChallengeController @Inject() (override val childController:TaskController,
                                     override val sessionManager: SessionManager,
                                     override val actionManager: ActionManager,
                                     override val dal: ChallengeDAL,
                                     surveyDAL: SurveyDAL,
                                     taskDAL: TaskDAL,
                                     userDAL: UserDAL,
                                     override val tagDAL: TagDAL)
  extends ParentController[Challenge, Task] with TagsMixin[Challenge] {

  // json reads for automatically reading Challenges from a posted json body
  override implicit val tReads: Reads[Challenge] = Challenge.challengeReads
  // json writes for automatically writing Challenges to a json body response
  override implicit val tWrites: Writes[Challenge] = Challenge.challengeWrites
  // json writes for automatically writing surveys to a json body response
  implicit val sWrites:Writes[Survey] = Survey.surveyWrites
  // json writes for automatically writing Tasks to a json body response
  override protected val cWrites: Writes[Task] = Task.taskWrites
  // json reads for automatically reading tasks from a posted json body
  override protected val cReads: Reads[Task] = Task.taskReads
  // The type of object that this controller deals with.
  override implicit val itemType = ChallengeType()

  override def dalWithTags:TagDALMixin[Challenge] = dal

  /**
    * This function allows sub classes to modify the body, primarily this would be used for inserting
    * default elements into the body that shouldn't have to be required to create an object.
    *
    * @param body The incoming body from the request
    * @return
    */
  override def updateCreateBody(body: JsValue, user:User): JsValue = {
    var jsonBody = super.updateCreateBody(body, user)
    jsonBody = Utils.insertIntoJson(jsonBody, "enabled", true)(BooleanWrites)
    jsonBody = Utils.insertIntoJson(jsonBody, "challengeType", Actions.ITEM_TYPE_CHALLENGE)(IntWrites)
    jsonBody = Utils.insertIntoJson(jsonBody, "difficulty", Challenge.DIFFICULTY_NORMAL)(IntWrites)
    jsonBody = Utils.insertIntoJson(jsonBody, "featured", false)(BooleanWrites)
    jsonBody = Utils.insertIntoJson(jsonBody, "defaultPriority", Challenge.PRIORITY_HIGH)(IntWrites)
    jsonBody = Utils.insertIntoJson(jsonBody, "defaultZoom", Challenge.DEFAULT_ZOOM)(IntWrites)
    jsonBody = Utils.insertIntoJson(jsonBody, "minZoom", Challenge.MIN_ZOOM)(IntWrites)
    jsonBody = Utils.insertIntoJson(jsonBody, "maxZoom", Challenge.MAX_ZOOM)(IntWrites)
    // if we can't find the parent ID, just use the user's default project instead
    (jsonBody \ "parent").asOpt[Long] match {
      case Some(v) => jsonBody
      case None => Utils.insertIntoJson(jsonBody, "parent", this.userDAL.getHomeProject(user).id)
    }
  }

  /**
    * Function can be implemented to extract more information than just the default create data,
    * to build other objects with the current object at the core. No data will be returned from this
    * function, it purely does work in the background AFTER creating the current object
    *
    * @param body          The Json body of data
    * @param createdObject The object that was created by the create function
    * @param user          The user that is executing the function
    */
  override def extractAndCreate(body: JsValue, createdObject: Challenge, user: User)(implicit c:Option[Connection]=None): Unit = {
    super.extractAndCreate(body, createdObject, user)
    this.extractTags(body, createdObject, user)
  }

  /**
    * Gets a json list of tags of the challenge
    *
    * @param id The id of the challenge containing the tags
    * @return The html Result containing json array of tags
    */
  // scalastyle:off
  @ApiOperation(
    nickname = "TagsForChallenge",
    value = "Get the tags for a challenge",
    notes =
      """This method will retrieve all the associated tags for a given challenge.""",
    httpMethod = "GET",
    produces = "application/json",
    protocols = "http",
    code = 200
  )
  @ApiResponses(Array(
    new ApiResponse(code = 404, message = "If the challenge is not found", response = classOf[StatusMessage])
  ))
  // scalastyle:on
  def getTagsForChallenge(
                           implicit @ApiParam(value="The id of challenge") id: Long
                         ) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      Ok(Json.toJson(this.getTags(id)))
    }
  }

  /**
    * Only slightly different from the base read function, if it detects that this is a survey
    * get the answers for the survey and wrap it up in a Survey object
    *
    * @param id The id of the object that is being retrieved
    * @return 200 Ok, 204 NoContent if not found
    */
  def getChallenge(implicit id:Long) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      this.dal.retrieveById match {
        case Some(value) =>
          if (value.challengeType == Actions.ITEM_TYPE_SURVEY) {
            val answers = this.surveyDAL.getAnswers(value.id)
            Ok(Json.toJson(Survey(value, answers)))
          } else {
            Ok(Json.toJson(value))
          }
        case None =>
          NoContent
      }
    }
  }

  /**
    * Gets the geo json for all the tasks associated with the challenge
    *
    * @param challengeId The challenge with the geojson
    * @param statusFilter Filtering by status of the tasks
    * @return
    */
  // scalastyle:off
  @ApiOperation(
    nickname = "ChallengeGeoJSON",
    value = "Get the geojson for a challenge",
    notes =
      """Gets the geo json for all the tasks associated with the challenge.""",
    httpMethod = "GET",
    produces = "application/json",
    protocols = "http",
    code = 200
  )
  @ApiResponses(Array(
    new ApiResponse(code = 404, message = "If the challenge is not found", response = classOf[StatusMessage])
  ))
  // scalastyle:on
  def getChallengeGeoJSON(
                           @ApiParam(value="The id of challenge") challengeId:Long,
                           @ApiParam(value="Comma separated list of status ids") statusFilter:String
                         ) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      this.dal.retrieveById(challengeId) match {
        case Some(c) =>
          val filter = if (StringUtils.isEmpty(statusFilter)) {
            None
          } else {
            Some(statusFilter.split(",").map(_.toInt).toList)
          }
          Ok(Json.parse(this.dal.getChallengeGeometry(challengeId, filter)))
        case None => throw new NotFoundException(s"No challenge with id $challengeId found.")
      }
    }
  }

  // scalastyle:off
  @ApiOperation(
    nickname = "ClusteredPoints",
    value = "Get the clustered points for a challenge",
    notes =
      """This method will retrieve the clustered points for a challenge, which is equivalent to the centroid point for each task in the challenge.""",
    httpMethod = "GET",
    produces = "application/json",
    protocols = "http",
    code = 200
  )
  @ApiResponses(Array(
    new ApiResponse(code = 404, message = "If the challenge is not found", response = classOf[StatusMessage])
  ))
  // scalastyle:on
  def getClusteredPoints(
                          @ApiParam(value="The id of challenge") challengeId:Long,
                          @ApiParam(value="Comma separated list of status ids") statusFilter:String
                        ) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      implicit val writes = ClusteredPoint.clusteredPointWrites
      val filter = if (StringUtils.isEmpty(statusFilter)) {
        None
      } else {
        Some(statusFilter.split(",").map(_.toInt).toList)
      }
      Ok(Json.toJson(this.dal.getClusteredPoints(challengeId, filter)))
    }
  }

  /**
    * Gets a random task that is a child of the challenge, includes the notion of priority
    *
    * @param challengeId The challenge id that is the parent of the tasks that you would be searching for.
    * @param taskSearch Filter based on the name of the task
    * @param tags A comma separated list of tags that optionally can be used to further filter the tasks
    * @param limit Limit of how many tasks should be returned
    * @return A list of Tasks that match the supplied filters
    */
  // scalastyle:off
  @ApiOperation(
    nickname = "RandomTasksWithPriority",
    value = "Get a random task based on priority in the challenge.",
    notes =
      """This method will retrieve random task(s) from a challenge based on the search parameters and the priority set by the challenge.""",
    httpMethod = "GET",
    produces = "application/json",
    protocols = "http",
    code = 200
  )
  @ApiResponses(Array(
    new ApiResponse(code = 404, message = "If the challenge is not found", response = classOf[StatusMessage])
  ))
  // scalastyle:on
  def getRandomTasksWithPriority(
                                  @ApiParam(value="The id of challenge") challengeId:Long,
                                  @ApiParam(value="Search filter by the name of the task") taskSearch:String,
                                  @ApiParam(value="Comma separated list of tags to filter by") tags:String,
                                  @ApiParam(value="Limit the number of results returned", defaultValue = "10") limit:Int
                                ) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      val params = SearchParameters(
        challengeId = Some(challengeId),
        taskSearch = taskSearch,
        taskTags = tags.split(",").toList
      )
      val result = this.taskDAL.getRandomTasksWithPriority(User.userOrMocked(user), params, limit)
      result.foreach(task => this.actionManager.setAction(user, this.itemType.convertToItem(task.id), TaskViewed(), ""))
      Ok(Json.toJson(result))
    }
  }

  /**
    * Gets a random task that is a child of the challenge.
    *
    * @param challengeId The challenge id that is the parent of the tasks that you would be searching for.
    * @param taskSearch Filter based on the name of the task
    * @param tags A comma separated list of tags that optionally can be used to further filter the tasks
    * @param limit Limit of how many tasks should be returned
    * @return A list of Tasks that match the supplied filters
    */
  // scalastyle:off
  @ApiOperation(
    nickname = "RandomTasks",
    value = "Get a random task in the challenge.",
    notes =
      """This method will retrieve random task(s) from a challenge based on the search parameters set by the challenge.""",
    httpMethod = "GET",
    produces = "application/json",
    protocols = "http",
    code = 200
  )
  @ApiResponses(Array(
    new ApiResponse(code = 404, message = "If the challenge is not found", response = classOf[StatusMessage])
  ))
  // scalastyle:on
  def getRandomTasks(
                      @ApiParam(value="The id of challenge") challengeId:Long,
                      @ApiParam(value="Search filter by the name of the task") taskSearch:String,
                      @ApiParam(value="Comma separated list of tags to filter by") tags:String,
                      @ApiParam(value="Limit the number of results returned", defaultValue = "10") limit:Int
                    ) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      val params = SearchParameters(
        challengeId = Some(challengeId),
        taskSearch = taskSearch,
        taskTags = tags.split(",").toList
      )
      val result = this.taskDAL.getRandomTasks(User.userOrMocked(user), params, limit)
      result.foreach(task => this.actionManager.setAction(user, this.itemType.convertToItem(task.id), TaskViewed(), ""))
      Ok(Json.toJson(result))
    }
  }

  /**
    * Gets the featured challenges
    *
    * @param limit The number of challenges to get
    * @param offset The offset
    * @return A Json array with the featured challenges
    */
  // scalastyle:off
  @ApiOperation(
    nickname = "FeaturedChallenges",
    value = "Get the featured challenges.",
    notes =
      """This method will retrieve a list of the featured challenges in the system.""",
    httpMethod = "GET",
    produces = "application/json",
    protocols = "http",
    code = 200
  )
  @ApiImplicitParams(Array(
    new ApiImplicitParam(
      name = "limit", value = "The number of challenges to return", defaultValue = "10", required = false, dataType = "long", paramType = "query"
    ),
    new ApiImplicitParam(
      name = "offset", value = "Used for paging.", defaultValue = "0", required = false, dataType = "string", paramType = "query"
    )
  ))
  // scalastyle:on
  def getFeaturedChallenges(
                             @ApiParam(value="The number of challenges to return", defaultValue = "10") limit:Int,
                             @ApiParam(value="Used for paging.", defaultValue = "0") offset:Int
                           ) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      Ok(Json.toJson(this.dal.getFeaturedChallenges(limit, offset)))
    }
  }
}
