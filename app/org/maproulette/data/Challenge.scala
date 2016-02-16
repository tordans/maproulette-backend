package org.maproulette.data

import org.maproulette.data.dal.ProjectDAL
import play.api.libs.json.{Reads, Json, Writes}

/**
  * @author cuthbertm
  */
case class Challenge(override val id: Long,
                     override val name: String,
                     parent:Long,
                     description: Option[String]=None,
                     blurb:Option[String]=None,
                     instruction:Option[String]=None) extends BaseObject[Long] {

  def getParent = ProjectDAL.retrieveById(parent)
}

object Challenge {
  implicit val challengeWrites: Writes[Challenge] = Json.writes[Challenge]
  implicit val challengeReads: Reads[Challenge] = Json.reads[Challenge]
}
