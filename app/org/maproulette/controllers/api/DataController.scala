package org.maproulette.controllers.api

import javax.inject.Inject

import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import org.maproulette.Config
import org.maproulette.actions.ActionManager
import org.maproulette.data._
import org.maproulette.models.dal.ChallengeDAL
import org.maproulette.session.SessionManager
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import play.api.mvc.{Action, Controller}

/**
  * @author cuthbertm
  */
class DataController @Inject() (sessionManager: SessionManager, challengeDAL: ChallengeDAL,
                                dataManager: DataManager, config:Config,
                                actionManager: ActionManager) extends Controller {

  implicit val actionWrites = actionManager.actionItemWrites
  implicit val dateWrites = Writes.dateWrites("yyyy-MM-dd")
  implicit val actionSummaryWrites = Json.writes[ActionSummary]
  implicit val userSummaryWrites = Json.writes[UserSummary]
  implicit val challengeSummaryWrites = Json.writes[ChallengeSummary]
  implicit val challengeActivityWrites = Json.writes[ChallengeActivity]

  implicit val stringIntMap:Writes[Map[String, Int]] = new Writes[Map[String, Int]] {
    def writes(map:Map[String, Int]) : JsValue =
      Json.obj(map.map{ case (s, i) =>
          val ret: (String, JsValueWrapper) = s.toString -> JsNumber(i)
          ret
      }.toSeq:_*)
  }

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

  def getUserChallengeSummary(challengeId:Long) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      Ok(Json.toJson(dataManager.getUserSummary(None, Some(challengeId))))
    }
  }

  def getUserSummary(projects:String) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      Ok(Json.toJson(dataManager.getUserSummary(getProjectList(projects), None)))
    }
  }

  def getChallengeSummary(id:Long) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      Ok(Json.toJson(
        dataManager.getChallengeSummary(None, Some(id))
      ))
    }
  }

  def getProjectSummary(projects:String) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      Ok(Json.toJson(
        dataManager.getChallengeSummary(getProjectList(projects), None)
      ))
    }
  }

  def getChallengeActivity(challengeId:Long, start:String, end:String) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      Ok(Json.toJson(
        dataManager.getChallengeActivity(None, Some(challengeId), getDate(start), getDate(end))
      ))
    }
  }

  def getProjectActivity(projects:String, start:String, end:String) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      Ok(Json.toJson(
        dataManager.getChallengeActivity(getProjectList(projects), None, getDate(start), getDate(end))
      ))
    }
  }

  private def getProjectList(projects:String) = if (projects.isEmpty) {
    None
  } else {
    Some(projects.split(",").toList.map(_.toLong))
  }

  private def getDate(date:String) = if (StringUtils.isEmpty(date)) {
    None
  } else {
    Some(DateTime.parse(date).toDate)
  }
}
