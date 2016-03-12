package org.maproulette.models

import play.api.libs.json.{Reads, Json, Writes}

/**
  * The project object is the root object of hierarchy, it is built to allow users to have personal
  * domains where they can create their own challenges and have a permissions model that allows
  * users to have and give control over what happens within that domain.
  *
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
