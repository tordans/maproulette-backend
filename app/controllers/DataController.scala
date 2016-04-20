package controllers

import com.google.inject.Inject
import org.maproulette.Config
import org.maproulette.actions.ActionManager
import org.maproulette.session.SessionManager
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}

/**
  * @author cuthbertm
  */
class DataController @Inject() (actionManager: ActionManager, sessionManager: SessionManager, config:Config) extends Controller {

  implicit val actionWrites = actionManager.actionItemWrites

  /**
    * Gets the recent activity for a user
    *
    * @param limit the limit on the number of activities return
    * @param offset paging, starting at 0
    * @return List of action summaries associated with the user
    */
  def getRecentUserActivity(limit:Int, offset:Int) = Action.async { implicit request =>
    val actualLimit = if (limit == -1) {
      config.numberOfActivities
    } else {
      limit
    }
    sessionManager.authenticatedRequest { user =>
      Ok(Json.toJson(actionManager.getRecentActivity(user.id, actualLimit, offset)))
    }
  }
}
