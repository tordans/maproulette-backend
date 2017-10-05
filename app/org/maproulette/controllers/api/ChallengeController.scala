// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.controllers.api

import java.sql.Connection
import javax.inject.Inject

import org.apache.commons.lang3.StringUtils
import org.maproulette.actions._
import org.maproulette.controllers.ParentController
import org.maproulette.exception.{NotFoundException, StatusMessage}
import org.maproulette.models.dal._
import org.maproulette.models._
import org.maproulette.services.ChallengeService
import org.maproulette.session.{SearchParameters, SessionManager, User}
import org.maproulette.utils.Utils
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent}

/**
  * The challenge controller handles all operations for the Challenge objects.
  * This includes CRUD operations and searching/listing.
  * See {@link org.maproulette.controllers.ParentController} for more details on parent object operations
  * See {@link org.maproulette.controllers.CRUDController} for more details on CRUD object operations
  *
  * @author cuthbertm
  */
class ChallengeController @Inject()(override val childController: TaskController,
                                    override val sessionManager: SessionManager,
                                    override val actionManager: ActionManager,
                                    override val dal: ChallengeDAL,
                                    dalManager: DALManager,
                                    override val tagDAL: TagDAL,
                                    challengeService: ChallengeService)
  extends ParentController[Challenge, Task] with TagsMixin[Challenge] {

  // json reads for automatically reading Challenges from a posted json body
  override implicit val tReads: Reads[Challenge] = Challenge.reads.challengeReads
  // json writes for automatically writing Challenges to a json body response
  override implicit val tWrites: Writes[Challenge] = Challenge.writes.challengeWrites
  // json writes for automatically writing Tasks to a json body response
  override protected val cWrites: Writes[Task] = Task.TaskFormat
  // json reads for automatically reading tasks from a posted json body
  override protected val cReads: Reads[Task] = Task.TaskFormat
  // The type of object that this controller deals with.
  override implicit val itemType: ItemType = ChallengeType()
  implicit val answerWrites: Writes[Answer] = Challenge.answerWrites

  override def dalWithTags: TagDALMixin[Challenge] = dal


  /**
    * Classes can override this function to inject values into the object before it is sent along
    * with the response
    *
    * @param obj the object being sent in the response
    * @return A Json representation of the object
    */
  override def inject(obj: Challenge) = {
    val tags = tagDAL.listByChallenge(obj.id)
    val withTagsJson = Utils.insertIntoJson(Json.toJson(obj), Tag.KEY, Json.toJson(tags.map(_.name)))
    obj.general.challengeType match {
      case Actions.ITEM_TYPE_SURVEY =>
        // if no answers provided with Challenge, then provide the default answers
        Utils.insertIntoJson(withTagsJson, Challenge.KEY_ANSWER, Json.toJson(this.dalManager.survey.getAnswers(obj.id)))
      case _ => withTagsJson
    }
  }

  /**
    * This function allows sub classes to modify the body, primarily this would be used for inserting
    * default elements into the body that shouldn't have to be required to create an object.
    *
    * @param body The incoming body from the request
    * @return
    */
  override def updateCreateBody(body: JsValue, user: User): JsValue = {
    var jsonBody = super.updateCreateBody(body, user)
    jsonBody = Utils.insertIntoJson(jsonBody, "owner", user.osmProfile.id, true)(LongWrites)
    jsonBody = Utils.insertIntoJson(jsonBody, "enabled", true)(BooleanWrites)
    jsonBody = Utils.insertIntoJson(jsonBody, "challengeType", Actions.ITEM_TYPE_CHALLENGE)(IntWrites)
    jsonBody = Utils.insertIntoJson(jsonBody, "difficulty", Challenge.DIFFICULTY_NORMAL)(IntWrites)
    jsonBody = Utils.insertIntoJson(jsonBody, "featured", false)(BooleanWrites)
    jsonBody = Utils.insertIntoJson(jsonBody, "checkinComment", "")(StringWrites)
    jsonBody = Utils.insertIntoJson(jsonBody, "defaultPriority", Challenge.PRIORITY_HIGH)(IntWrites)
    jsonBody = Utils.insertIntoJson(jsonBody, "defaultZoom", Challenge.DEFAULT_ZOOM)(IntWrites)
    jsonBody = Utils.insertIntoJson(jsonBody, "minZoom", Challenge.MIN_ZOOM)(IntWrites)
    jsonBody = Utils.insertIntoJson(jsonBody, "maxZoom", Challenge.MAX_ZOOM)(IntWrites)
    jsonBody = Utils.insertIntoJson(jsonBody, "updateTasks", false)(BooleanWrites)
    // if we can't find the parent ID, just use the user's default project instead
    (jsonBody \ "parent").asOpt[Long] match {
      case Some(v) => jsonBody
      case None => Utils.insertIntoJson(jsonBody, "parent", this.dalManager.user.getHomeProject(user).id)
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
  override def extractAndCreate(body: JsValue, createdObject: Challenge, user: User)(implicit c: Option[Connection] = None): Unit = {
    (body \ "localGeoJSON").asOpt[JsValue] match {
      case Some(local) => challengeService.buildChallengeTasks(user, createdObject, Some(Json.stringify(local)))
      case None => super.extractAndCreate(body, createdObject, user)
    }
    this.extractTags(body, createdObject, user)
  }

  /**
    * Gets a json list of tags of the challenge
    *
    * @param id The id of the challenge containing the tags
    * @return The html Result containing json array of tags
    */
  def getTagsForChallenge(id: Long): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      Ok(Json.toJson(this.getTags(id)))
    }
  }

  /**
    * Gets the geo json for all the tasks associated with the challenge
    *
    * @param challengeId  The challenge with the geojson
    * @param statusFilter Filtering by status of the tasks
    * @return
    */
  def getChallengeGeoJSON(challengeId: Long, statusFilter: String): Action[AnyContent] = Action.async { implicit request =>
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

  def getClusteredPoints(challengeId: Long, statusFilter: String): Action[AnyContent] = Action.async { implicit request =>
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
    * @param taskSearch  Filter based on the name of the task
    * @param tags        A comma separated list of tags that optionally can be used to further filter the tasks
    * @param limit       Limit of how many tasks should be returned
    * @return A list of Tasks that match the supplied filters
    */
  def getRandomTasksWithPriority(challengeId: Long, taskSearch: String, tags: String, limit: Int): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      val params = SearchParameters(
        challengeId = Some(challengeId),
        taskSearch = Some(taskSearch),
        taskTags = Some(tags.split(",").toList)
      )
      val result = this.dalManager.task.getRandomTasksWithPriority(User.userOrMocked(user), params, limit)
      result.foreach(task => this.actionManager.setAction(user, this.itemType.convertToItem(task.id), TaskViewed(), ""))
      Ok(Json.toJson(result))
    }
  }

  /**
    * Gets a random task that is a child of the challenge.
    *
    * @param challengeId The challenge id that is the parent of the tasks that you would be searching for.
    * @param taskSearch  Filter based on the name of the task
    * @param tags        A comma separated list of tags that optionally can be used to further filter the tasks
    * @param limit       Limit of how many tasks should be returned
    * @return A list of Tasks that match the supplied filters
    */
  def getRandomTasks(challengeId: Long, taskSearch: String, tags: String, limit: Int): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      val params = SearchParameters(
        challengeId = Some(challengeId),
        taskSearch = Some(taskSearch),
        taskTags = Some(tags.split(",").toList)
      )
      val result = this.dalManager.task.getRandomTasks(User.userOrMocked(user), params, limit)
      result.foreach(task => this.actionManager.setAction(user, this.itemType.convertToItem(task.id), TaskViewed(), ""))
      Ok(Json.toJson(result))
    }
  }

  /**
    * Gets the featured challenges
    *
    * @param limit  The number of challenges to get
    * @param offset The offset
    * @return A Json array with the featured challenges
    */
  def getFeaturedChallenges(limit: Int, offset: Int): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      Ok(Json.toJson(this.dal.getFeaturedChallenges(limit, offset)))
    }
  }

  def updateTaskPriorities(challengeId: Long): Action[AnyContent] = Action.async { implicit request =>
    implicit val requireSuperUser = true
    this.sessionManager.authenticatedRequest { implicit user =>
      val challengeIds = if (challengeId < 0) {
        val challengeList = this.dal.find("%", -1)
        challengeList.foreach {
          challenge => this.dal.updateTaskPriorities(user)(challenge.id)
        }
        challengeList.map(_.id)
      } else {
        this.dal.updateTaskPriorities(user)(challengeId)
        List(challengeId)
      }
      Ok(Json.toJson(StatusMessage("OK", JsString(s"Priorities updated for challenges ${challengeIds.mkString(",")}"))))
    }
  }

  /**
    * Resets the task instructions for all the children tasks of the supplied challenge
    *
    * @param challengeId id of the parent challenge
    * @return 200 empty Ok
    */
  def resetTaskInstructions(challengeId:Long) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      this.dal.resetTaskInstructions(user, challengeId)
      Ok
    }
  }

  /**
    * Deletes all the tasks under a challenge based on the status of the task. If no filter for the
    * status is supplied, then will delete all the tasks
    *
    * @param challengeId The id of the challenge
    * @param statusFilters A comma separated list of status' to filter the deletion by.
    * @return
    */
  def deleteTasks(challengeId:Long, statusFilters:String="") : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      this.dal.deleteTasks(user, challengeId, statusFilters.split(",").map(_.toInt).toList)
      Ok
    }
  }

  /**
    * Uses the search parameters from the query string to find challenges
    *
    * @param limit limits the amount of results returned
    * @param page paging mechanism for limited results
    * @return A list of challenges matching the query string parameters
    */
  def extendedFind(limit:Int, page:Int) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      SearchParameters.withQSSearch { implicit params =>
        val challenges = this.dal.extendedFind(params, limit, page)
        if (challenges.isEmpty) {
          NotFound
        } else {
          val tags = this.tagDAL.listByChallenges(challenges.map(c => c.id))
          val jsonList = challenges.map { c =>
            Utils.insertIntoJson(Json.toJson(c), Tag.KEY, Json.toJson(tags.getOrElse(c.id, List.empty).map(_.name)))
          }
          Ok(Json.toJson(jsonList))
        }
      }
    }
  }
}
