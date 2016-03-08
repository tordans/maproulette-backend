package org.maproulette.controllers.api

import javax.inject.Inject

import org.maproulette.actions.{ActionManager, ActionSummary}
import org.maproulette.session.SessionManager
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}

/**
  * @author cuthbertm
  */
class ActionController @Inject() extends Controller {

  implicit val summaryReads = Json.writes[ActionSummary]

  def getFullSummary() = Action.async { implicit request =>
    SessionManager.authenticatedRequest { implicit user =>
      Ok(Json.toJson(ActionManager.getFullSummary()))
    }
  }
}
