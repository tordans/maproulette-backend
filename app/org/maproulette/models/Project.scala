package org.maproulette.models

import play.api.data._
import play.api.data.Forms._
import org.maproulette.session.{Group, User}
import play.api.libs.json.{Json, Reads, Writes}

/**
  * The project object is the root object of hierarchy, it is built to allow users to have personal
  * domains where they can create their own challenges and have a permissions model that allows
  * users to have and give control over what happens within that domain.
  *
  * @author cuthbertm
  */
case class Project(override val id: Long,
                   override val name: String,
                   override val description: Option[String]=None,
                   groups:List[Group]=List.empty,
                   enabled:Boolean=true) extends BaseObject[Long] {

  /**
    * Checks whether the user currently has admin access to this project
    *
    * @param user
    * @return
    */
  override def hasWriteAccess(user:User) =
    user.isSuperUser || groups.exists(g => user.groups.exists(ugid => g.id == ugid.id))
}

object Project {
  implicit val groupWrites: Writes[Group] = Json.writes[Group]
  implicit val groupReads: Reads[Group] = Json.reads[Group]
  implicit val projectWrites: Writes[Project] = Json.writes[Project]
  implicit val projectReads: Reads[Project] = Json.reads[Project]

  val projectForm = Form(
    mapping(
      "id" -> default(longNumber,-1L),
      "name" -> nonEmptyText,
      "description" -> optional(text),
      "groups" -> list(
        mapping(
          "id" -> longNumber,
          "name" -> nonEmptyText,
          "projectId" -> longNumber,
          "groupType" -> number(min = 1, max = 1)
        )(Group.apply)(Group.unapply)
      ),
      "enabled" -> boolean
    )(Project.apply)(Project.unapply)
  )

  def emptyProject = Project(-1, "")
}
