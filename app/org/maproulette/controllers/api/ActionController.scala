package org.maproulette.controllers.api

import javax.inject.Inject

import org.maproulette.actions.{ActionManager, ActionSummary}
import org.maproulette.session.SessionManager
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}

/**
  * Actions for the controller for the actions of the tasks in the system
  *
  * @author cuthbertm
  */
class ActionController @Inject() (sessionManager: SessionManager, actionManager: ActionManager) extends Controller {

  implicit val summaryReads = Json.writes[ActionSummary]

  /**
    * Get the full summary of all the actions of all the tasks. This function will return
    * information on pretty much every task in the system, so this should really only be used
    * for testing purposes.
    * Must be authenticated to perform operation
    *
    * @return Json response body containing array of ActionSummary objects
    */
  def getFullSummary() = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      Ok(Json.toJson(actionManager.getFullSummary()))
    }
  }
}
