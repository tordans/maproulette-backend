package controllers

import org.maproulette.controllers.ParentController
import org.maproulette.data.{Task, Challenge}
import org.maproulette.data.dal.ChallengeDAL
import play.api.libs.json.{Writes, Reads}

/**
  * @author cuthbertm
  */
object ChallengeController extends ParentController[Challenge, Task] {
  override protected val dal = ChallengeDAL
  override implicit val tReads: Reads[Challenge] = Challenge.challengeReads
  override implicit val tWrites: Writes[Challenge] = Challenge.challengeWrites
  override protected val cWrites: Writes[Task] = Task.taskWrites
}
