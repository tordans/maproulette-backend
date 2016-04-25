package org.maproulette.controllers.api

import javax.inject.Inject

import org.maproulette.data.DataManager
import org.maproulette.exception.NotFoundException
import org.maproulette.models.Task
import org.maproulette.models.dal.ChallengeDAL
import org.maproulette.session.SessionManager
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json.{JsNumber, JsValue, Json, Writes}
import play.api.mvc.{Action, Controller}

/**
  * @author cuthbertm
  */
class DataController @Inject() (sessionManager: SessionManager, challengeDAL: ChallengeDAL,
                                dataManager: DataManager) extends Controller {

  implicit val stringIntMap:Writes[Map[String, Int]] = new Writes[Map[String, Int]] {
    def writes(map:Map[String, Int]) : JsValue =
      Json.obj(map.map{ case (s, i) =>
          val ret: (String, JsValueWrapper) = s.toString -> JsNumber(i)
          ret
      }.toSeq:_*)
  }

  def getChallengeSummary(id:Long) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      challengeDAL.retrieveById(id) match {
        case Some(c) =>
          val summary = dataManager.getChallengeSummary(c).map(v => Task.getStatusName(v._1).getOrElse("Unknown") -> v._2)
          Ok(Json.obj(
            "name" -> c.name,
            "statusCounts" -> Json.toJson(summary)
          ))
        case None => throw new NotFoundException(s"No challenge with id [$id] found.")
      }
    }
  }
}
