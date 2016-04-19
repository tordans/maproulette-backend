package org.maproulette.session

import org.maproulette.actions.Actions
import org.maproulette.models.BaseObject

/**
  * @author cuthbertm
  */
case class Group(override val id:Long,
                 override val name:String,
                 projectId:Long,
                 groupType:Int) extends BaseObject[Long] {
  override val itemType: Int = Actions.ITEM_TYPE_GROUP
}

object Group {
  val TYPE_SUPER_USER = -1
  val TYPE_ADMIN = 1
}
