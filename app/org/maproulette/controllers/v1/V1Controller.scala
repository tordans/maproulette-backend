package org.maproulette.controllers.v1

import javax.inject.Inject

import org.maproulette.models.Task
import org.maproulette.models.dal.ChallengeDAL
import org.maproulette.session.SessionManager
import play.api.libs.json.Json
import play.api.mvc.{Action, BodyParsers, Controller}

/**
  * @author cuthbertm
  */
class V1Controller @Inject() (sessionManager: SessionManager, challengeDAL: ChallengeDAL) extends Controller {

  def getStatusOfTasks(challenge:String) = Action { implicit request =>
    NoContent
  }

  def createChallenge(challenge:String) = Action(BodyParsers.parse.json) { implicit request =>
    NoContent
  }

  def updateChallenge(challenge:String) = Action(BodyParsers.parse.json) { implicit request =>
    NoContent
  }

  def createTask(challenge:String, taskIdentifier:String) = Action(BodyParsers.parse.json) { implicit request =>
    NoContent
  }

  def updateTask(challenge:String, taskIdentifier:String) = Action(BodyParsers.parse.json) { implicit request =>
    NoContent
  }

  def createTasks(challenge:String) = Action(BodyParsers.parse.json) { implicit request =>
    NoContent
  }

  def updateTasks(challenge:String) = Action(BodyParsers.parse.json) { implicit request =>
    NoContent
  }

  //-----STATS API --------------
  //-----------------------------

  def listChallenges(difficulty:Int, all:Boolean, contains:String) = Action(BodyParsers.parse.json) { implicit request =>
    NoContent
  }

  def getChallenge(implicit challenge:String) = Action { implicit request =>
    challengeDAL.retrieveByIdentifier match {
      case Some(obj) => NoContent
      case None => NoContent
    }
  }

  def getChallengeSummary(implicit challenge:String) = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      challengeDAL.retrieveByIdentifier match {
        case Some(obj) =>
          val summaryMap = challengeDAL.getSummary(obj.id)
          val total = summaryMap.foldLeft(0)(_+_._2)
          val available = summaryMap.foldLeft(0)((total,element) =>
            if (element == Task.STATUS_CREATED || element == Task.STATUS_SKIPPED) total + element._2 else total
          )
          Ok(Json.obj("available" -> available, "total" -> total))
        case None => Ok(Json.obj("available" -> 0, "total" -> 0))
      }
    }
  }

  def getChallengePolygon(challenge:String) = Action { implicit request =>
    NoContent
  }

  def getRandomChallengeTask(challenge:String) = Action { implicit request =>
    NoContent
  }

  def getChallengeTask(challenge:String, taskIdentifier:String) = Action { implicit request =>
    NoContent
  }

  def updateTaskStatus(challenge:String, taskIdentifier:String) = Action { implicit request =>
    NoContent
  }

  def getTaskGeometries(challenge:String, taskIdentifier:String) = Action { implicit request =>
    NoContent
  }
}
