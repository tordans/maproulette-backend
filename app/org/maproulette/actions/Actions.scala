package org.maproulette.actions

/**
  * @author cuthbertm
  */
sealed class ActionType(id:Int, level:Int) {
  def getId = id
  def getLevel = level
}


sealed class ItemType(id:Int) {
  val typeId = id
  def convertToItem(itemId:Long) = {
    this match {
      case p:Project => new ProjectItem(itemId)
      case c:Challenge => new ChallengeItem(itemId)
      case t:Task => new TaskItem(itemId)
      case ta:Tag => new TagItem(itemId)
    }
  }
}

sealed trait Item {
  def itemId:Long
}

case class Project() extends ItemType(0)
case class Challenge() extends ItemType(1)
case class Task() extends ItemType(2)
case class Tag() extends ItemType(3)

class ProjectItem(override val itemId:Long) extends Project with Item
class ChallengeItem(override val itemId:Long) extends Challenge with Item
class TaskItem(override val itemId:Long) extends Task with Item
class TagItem(override val itemId:Long) extends Tag with Item

case class Updated() extends ActionType(0, Actions.ACTION_LEVEL_2)
case class Created() extends ActionType(1, Actions.ACTION_LEVEL_2)
case class Deleted() extends ActionType(2, Actions.ACTION_LEVEL_2)
case class TaskViewed() extends ActionType(3, Actions.ACTION_LEVEL_3)
case class TaskStatusSet(status:Int) extends ActionType(4, Actions.ACTION_LEVEL_1)
case class TagAdded() extends ActionType(5, Actions.ACTION_LEVEL_2)
case class TagRemoved() extends ActionType(6, Actions.ACTION_LEVEL_2)

object Actions {
  val ACTION_LEVEL_1 = 1
  val ACTION_LEVEL_2 = 2
  val ACTION_LEVEL_3 = 3
}
