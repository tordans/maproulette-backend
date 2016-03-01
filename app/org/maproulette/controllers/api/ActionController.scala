package org.maproulette.controllers.api

import org.maproulette.actions.{ActionManager, ActionSummary}
import org.maproulette.exception.MPExceptionUtil
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}

/**
  * @author cuthbertm
  */
object ActionController extends Controller {

  implicit val summaryReads = Json.writes[ActionSummary]

  def getFullSummary() = Action {
    MPExceptionUtil.internalExceptionCatcher { () =>
      Ok(Json.toJson(ActionManager.getFullSummary()))
    }
  }
}
