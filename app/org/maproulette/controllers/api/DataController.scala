// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.controllers.api

import javax.inject.Inject

import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import org.maproulette.Config
import org.maproulette.actions.ActionManager
import org.maproulette.data._
import org.maproulette.models.Challenge
import org.maproulette.models.dal.ChallengeDAL
import org.maproulette.session.SessionManager
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, AnyContentAsFormUrlEncoded, Controller}

/**
  * @author cuthbertm
  */
class DataController @Inject() (sessionManager: SessionManager, challengeDAL: ChallengeDAL,
                                dataManager: DataManager, config:Config,
                                actionManager: ActionManager) extends Controller {

  implicit val actionWrites = actionManager.actionItemWrites
  implicit val dateWrites = Writes.dateWrites("yyyy-MM-dd")
  implicit val userSurveySummaryWrites = Json.writes[UserSurveySummary]
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
  def getRecentUserActivity(limit:Int, offset:Int) : Action[AnyContent] = Action.async { implicit request =>
    val actualLimit = if (limit == -1) {
      this.config.numberOfActivities
    } else {
      limit
    }
    this.sessionManager.authenticatedRequest { user =>
      Ok(Json.toJson(this.actionManager.getRecentActivity(user, actualLimit, offset)))
    }
  }

  def getUserChallengeSummary(challengeId:Long, start:String, end:String, survey:Int, priority:Int) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      if (survey == 1) {
        Ok(Json.toJson(this.dataManager.getUserSurveySummary(None, Some(challengeId), this.getDate(start), getDate(end), this.getPriority(priority))))
      } else {
        Ok(Json.toJson(this.dataManager.getUserChallengeSummary(None, Some(challengeId), this.getDate(start), getDate(end), this.getPriority(priority))))
      }
    }
  }

  def getUserSummary(projects:String, start:String, end:String, survey:Int, priority:Int) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      if (survey == 1) {
        Ok(Json.toJson(this.dataManager.getUserSurveySummary(
          this.getProjectList(projects), None, this.getDate(start), getDate(end), this.getPriority(priority)
        )))
      } else {
        Ok(Json.toJson(this.dataManager.getUserChallengeSummary(
          this.getProjectList(projects), None, this.getDate(start), getDate(end), this.getPriority(priority)
        )))
      }
    }
  }

  def getChallengeSummary(id:Long, priority:Int) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      Ok(Json.toJson(
        this.dataManager.getChallengeSummary(challengeId = Some(id), priority = this.getPriority(priority))
      ))
    }
  }

  def getProjectSummary(projects:String) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      Ok(Json.toJson(
        this.dataManager.getChallengeSummary(this.getProjectList(projects))
      ))
    }
  }

  def getChallengeActivity(challengeId:Long, start:String, end:String, priority:Int) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      Ok(Json.toJson(
        this.dataManager.getChallengeActivity(None, Some(challengeId), this.getDate(start), this.getDate(end), this.getPriority(priority))
      ))
    }
  }

  def getProjectActivity(projects:String, start:String, end:String) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      Ok(Json.toJson(
        this.dataManager.getChallengeActivity(this.getProjectList(projects), None, this.getDate(start), this.getDate(end))
      ))
    }
  }

  /**
    * Special API for handling data table API requests for challenge summary table
    *
    * @param projectIds A comma separated list of projects to filter by
    * @return
    */
  def getChallengeSummaries(projectIds:String, priority:Int) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      val postData = request.body.asInstanceOf[AnyContentAsFormUrlEncoded].data
      val draw = postData.get("draw").head.head.toInt
      val start = postData.get("start").head.head.toInt
      val length = postData.get("length").head.head.toInt
      val search = postData.get("search[value]").head.head
      val orderDirection = postData.get("order[0][dir]").head.head.toUpperCase
      val orderColumnID = postData.get("order[0][column]").head.head.toInt
      val orderColumnName = postData.get(s"columns[$orderColumnID][name]").head.headOption
      val projectList = this.getProjectList(projectIds)
      val challengeSummaries =
        this.dataManager.getChallengeSummary(projectList, None, length, start, orderColumnName, orderDirection, search, this.getPriority(priority))

      val summaryMap = challengeSummaries.map(summary => Map(
        "id" -> summary.id.toString,
        "name" -> summary.name,
        "complete_percentage" -> summary.actions.percentComplete.toString,
        "available" -> summary.actions.trueAvailable.toString,
        "available_perc" -> summary.actions.percentage(summary.actions.available).toString,
        "fixed" -> summary.actions.fixed.toString,
        "fixed_perc" -> summary.actions.percentage(summary.actions.fixed).toString,
        "false_positive" -> summary.actions.falsePositive.toString,
        "false_positive_perc" -> summary.actions.percentage(summary.actions.falsePositive).toString,
        "skipped" -> summary.actions.skipped.toString,
        "skipped_perc" -> summary.actions.percentage(summary.actions.skipped).toString,
        "already_fixed" -> summary.actions.alreadyFixed.toString,
        "already_fixed_perc" -> summary.actions.percentage(summary.actions.alreadyFixed).toString,
        "too_hard" -> summary.actions.tooHard.toString,
        "too_hard_perc" -> summary.actions.percentage(summary.actions.tooHard).toString
      ))
      Ok(Json.obj(
        "draw" -> JsNumber(draw),
        "recordsTotal" -> JsNumber(dataManager.getTotalSummaryCount(projectList, None)),
        "recordsFiltered" -> JsNumber(dataManager.getTotalSummaryCount(projectList, None, search)),
        "data" -> Json.toJson(summaryMap)
      ))
    }
  }

  private def getPriority(priority:Int) : Option[Int] = {
    priority match {
      case x if x >= Challenge.PRIORITY_HIGH & x <= Challenge.PRIORITY_LOW => Some(x)
      case _ => None
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
