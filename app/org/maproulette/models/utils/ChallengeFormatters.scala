package org.maproulette.models.utils

import org.maproulette.models._
import play.api.libs.json._
import play.api.libs.functional.syntax._

/**
  * @author cuthbertm
  */
trait ChallengeWrites {
  implicit val challengeGeneralWrites: Writes[ChallengeGeneral] = Json.writes[ChallengeGeneral]
  implicit val challengeCreationWrites: Writes[ChallengeCreation] = Json.writes[ChallengeCreation]
  implicit val challengePriorityWrites: Writes[ChallengePriority] = Json.writes[ChallengePriority]
  implicit val challengeExtraWrites: Writes[ChallengeExtra] = Json.writes[ChallengeExtra]

  implicit val challengeWrites: Writes[Challenge] = (
    (JsPath \ "id").write[Long] and
    (JsPath \ "name").write[String] and
    (JsPath \ "description").writeNullable[String] and
    JsPath.write[ChallengeGeneral] and
    JsPath.write[ChallengeCreation] and
    JsPath.write[ChallengePriority] and
    JsPath.write[ChallengeExtra] and
    (JsPath \ "status").writeNullable[Int]
  )(unlift(Challenge.unapply))
}

trait ChallengeReads extends DefaultReads {
  implicit val challengeGeneralReads: Reads[ChallengeGeneral] = Json.reads[ChallengeGeneral]
  implicit val challengeCreationReads: Reads[ChallengeCreation] = Json.reads[ChallengeCreation]
  implicit val challengePriorityReads: Reads[ChallengePriority] = Json.reads[ChallengePriority]
  implicit val challengeExtraReads: Reads[ChallengeExtra] = Json.reads[ChallengeExtra]

  implicit val challengeReads: Reads[Challenge] = (
    (JsPath \ "id").read[Long] and
    (JsPath \ "name").read[String] and
    (JsPath \ "description").readNullable[String] and
    JsPath.read[ChallengeGeneral] and
    JsPath.read[ChallengeCreation] and
    JsPath.read[ChallengePriority] and
    JsPath.read[ChallengeExtra] and
    (JsPath \ "status").readNullable[Int]
  )(Challenge.apply _)
}
