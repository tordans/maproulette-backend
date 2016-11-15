// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.controllers.api

import javax.inject.Inject

import io.swagger.annotations._
import org.maproulette.exception.{StatusMessage, StatusMessages}
import org.maproulette.models.Challenge
import org.maproulette.models.dal.DALManager
import org.maproulette.session.SessionManager
import play.api.libs.json.{JsString, Json}
import play.api.mvc.{Action, AnyContent, Controller}

/**
  * A basic action controller for miscellaneous operations on the API
  *
  * @author cuthbertm
  */
class APIController @Inject() (dalManager: DALManager, sessionManager: SessionManager) extends Controller with StatusMessages {

  implicit val challengeWrites = Challenge.writes.challengeWrites

  // scalastyle:off
  @ApiOperation(
    nickname = "getSavedChallenges",
    value = "Get the List of Saved Challenges for a specific user",
    notes =
      """This method will simply retrieve the list of saved challenges for the provided user.""",
    httpMethod = "GET",
    produces = "application/json",
    consumes = "application/json",
    protocols = "http",
    code = 200
  )
  @ApiImplicitParams(Array(
    new ApiImplicitParam(
      name = "apiKey", value = "The apikey to authorize the request", required = true, dataType = "string", paramType = "header"
    )
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 404, message = "If the user is not found", response = classOf[StatusMessage])
  ))
  // scalastyle:on
  def getSavedChallenges(
                          @ApiParam(value="The id of the user your are requesting the saved challenges for") userId:Long
                        ) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      Ok(Json.toJson(this.dalManager.user.getSavedChallenges(userId, user)))
    }
  }

  def saveChallenge(userId:Long, challengeId:Long) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      dalManager.user.saveChallenge(userId, challengeId, user)
      Ok(Json.toJson(StatusMessage("OK", JsString(s"Challenge $challengeId saved for user $userId"))))
    }
  }

  /**
    * In the routes file this will be mapped to any /api/v2/ paths. It is the last mapping to take
    * place so if it doesn't match any of the other routes it will fall into this invalid path.
    *
    * @param path The path found after /api/v2
    * @return A json object returned with a 400 BadRequest
    */
  def invalidAPIPath(path:String) : Action[AnyContent] = Action {
    BadRequest(Json.toJson(StatusMessage("KO", JsString(s"Invalid Path [$path] for API"))))
  }
}
