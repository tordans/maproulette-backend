package controllers

import org.maproulette.controllers.ParentController
import org.maproulette.data.{Challenge, Project}
import org.maproulette.data.dal.ProjectDAL
import play.api.libs.json._

/**
  * @author cuthbertm
  */
object ProjectController extends ParentController[Project, Challenge] {
  override protected val dal = ProjectDAL
  override implicit val tReads: Reads[Project] = Project.projectReads
  override implicit val tWrites: Writes[Project] = Project.projectWrites
  override protected val cWrites: Writes[Challenge] = Challenge.challengeWrites
  override protected val cReads: Reads[Challenge] = Challenge.challengeReads
  override protected val childController = ChallengeController
}
