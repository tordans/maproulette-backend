package org.maproulette.models

import org.maproulette.models.dal.ProjectDAL
import play.api.libs.json.{Reads, Json, Writes}

/**
  * @author cuthbertm
  */
case class Challenge(override val id: Long,
                     override val name: String,
                     override val identifier:Option[String]=None,
                     parent:Long,
                     difficulty:Option[Int]=None,
                     description: Option[String]=None,
                     blurb:Option[String]=None,
                     instruction:Option[String]=None) extends BaseObject[Long] {

  def getParent = ProjectDAL.retrieveById(parent)
}

object Challenge {
  implicit val challengeWrites: Writes[Challenge] = Json.writes[Challenge]
  implicit val challengeReads: Reads[Challenge] = Json.reads[Challenge]

  val DIFFICULTY_EASY = 1
  val DIFFICULTY_NORMAL = 2
  val DIFFICULTY_EXPERT = 3
}
