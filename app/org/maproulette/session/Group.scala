package org.maproulette.session

import org.maproulette.models.BaseObject

/**
  * @author cuthbertm
  */
case class Group(override val id:Long,
                 override val name:String,
                 projectId:Long,
                 groupType:Int) extends BaseObject[Long]

object Group {
  val TYPE_SUPER_USER = -1
  val TYPE_ADMIN = 1
}
