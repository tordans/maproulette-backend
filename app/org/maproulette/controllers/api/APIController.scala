// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.controllers.api

import javax.inject.Inject

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

  def getSavedChallenges(userId:Long) : Action[AnyContent] = Action.async { implicit request =>
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

  def unsaveChallenge(userId:Long, challengeId:Long) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      dalManager.user.unsaveChallenge(userId, challengeId, user)
      Ok(Json.toJson(StatusMessage("OK", JsString(s"Challenge $challengeId unsaved from user $userId"))))
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
