package org.maproulette.data

import play.api.libs.json.{Reads, Json, Writes}

/**
  * @author cuthbertm
  */
case class Project(override val id: Long,
                   override val name: String,
                   description: Option[String]=None) extends BaseObject[Long] {
}

object Project {
  implicit val projectWrites: Writes[Project] = Json.writes[Project]
  implicit val projectReads: Reads[Project] = Json.reads[Project]
}
