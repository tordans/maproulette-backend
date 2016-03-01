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
      case p:ProjectType => new ProjectItem(itemId)
      case c:ChallengeType => new ChallengeItem(itemId)
      case t:TaskType => new TaskItem(itemId)
      case ta:TagType => new TagItem(itemId)
    }
  }
}

sealed trait Item {
  def itemId:Long
}

case class ProjectType() extends ItemType(Actions.ITEM_TYPE_PROJECT)
case class ChallengeType() extends ItemType(Actions.ITEM_TYPE_CHALLENGE)
case class TaskType() extends ItemType(Actions.ITEM_TYPE_TASK)
case class TagType() extends ItemType(Actions.ITEM_TYPE_TAG)

class ProjectItem(override val itemId:Long) extends ProjectType with Item
class ChallengeItem(override val itemId:Long) extends ChallengeType with Item
class TaskItem(override val itemId:Long) extends TaskType with Item
class TagItem(override val itemId:Long) extends TagType with Item

case class Updated() extends ActionType(Actions.ACTION_TYPE_UPDATED, Actions.ACTION_LEVEL_2)
case class Created() extends ActionType(Actions.ACTION_TYPE_CREATED, Actions.ACTION_LEVEL_2)
case class Deleted() extends ActionType(Actions.ACTION_TYPE_DELETED, Actions.ACTION_LEVEL_2)
case class TaskViewed() extends ActionType(Actions.ACTION_TYPE_TASK_VIEWED, Actions.ACTION_LEVEL_3)
case class TaskStatusSet(status:Int) extends ActionType(Actions.ACTION_TYPE_TASK_STATUS_SET, Actions.ACTION_LEVEL_1)
case class TagAdded() extends ActionType(Actions.ACTION_TYPE_TAG_ADDED, Actions.ACTION_LEVEL_2)
case class TagRemoved() extends ActionType(Actions.ACTION_TYPE_TAG_REMOVED, Actions.ACTION_LEVEL_2)

object Actions {
  val ACTION_LEVEL_1 = 1
  val ACTION_LEVEL_2 = 2
  val ACTION_LEVEL_3 = 3

  val ITEM_TYPE_PROJECT = 0
  val ITEM_TYPE_PROJECT_NAME = "Project"
  val ITEM_TYPE_CHALLENGE = 1
  val ITEM_TYPE_CHALLENGE_NAME = "Challenge"
  val ITEM_TYPE_TASK = 2
  val ITEM_TYPE_TASK_NAME = "Task"
  val ITEM_TYPE_TAG = 3
  val ITEM_TYPE_TAG_NAME = "Tag"

  val ACTION_TYPE_UPDATED = 0
  val ACTION_TYPE_UPDATED_NAME = "Updated"
  val ACTION_TYPE_CREATED = 1
  val ACTION_TYPE_CREATED_NAME = "Created"
  val ACTION_TYPE_DELETED = 2
  val ACTION_TYPE_DELETED_NAME = "Deleted"
  val ACTION_TYPE_TASK_VIEWED = 3
  val ACTION_TYPE_TASK_VIEWED_NAME = "Task_Viewed"
  val ACTION_TYPE_TASK_STATUS_SET = 4
  val ACTION_TYPE_TASK_STATUS_SET_NAME = "Task_Status_Set"
  val ACTION_TYPE_TAG_ADDED = 5
  val ACTION_TYPE_TAG_ADDED_NAME = "Tag_Added"
  val ACTION_TYPE_TAG_REMOVED = 6
  val ACTION_TYPE_TAG_REMOVED_NAME = "Tag_Removed"

  def validActionType(actionType:Int) : Boolean = actionType >= ACTION_TYPE_UPDATED && actionType <= ACTION_TYPE_TAG_REMOVED

  def validItemType(itemType:Int) : Boolean = itemType >= ITEM_TYPE_PROJECT && itemType <= ITEM_TYPE_TAG

  def getTypeName(itemType:Int) : Option[String] = itemType match {
    case ITEM_TYPE_PROJECT => Some(ITEM_TYPE_PROJECT_NAME)
    case ITEM_TYPE_CHALLENGE => Some(ITEM_TYPE_CHALLENGE_NAME)
    case ITEM_TYPE_TASK => Some(ITEM_TYPE_TASK_NAME)
    case ITEM_TYPE_TAG => Some(ITEM_TYPE_TAG_NAME)
    case _ => None
  }

  def getTypeID(itemType:String) : Option[Int] = itemType.toLowerCase match {
    case t if t.equalsIgnoreCase(ITEM_TYPE_PROJECT_NAME.toLowerCase) => Some(ITEM_TYPE_PROJECT)
    case t if t.equalsIgnoreCase(ITEM_TYPE_CHALLENGE_NAME.toLowerCase) => Some(ITEM_TYPE_CHALLENGE)
    case t if t.equalsIgnoreCase(ITEM_TYPE_TASK_NAME.toLowerCase) => Some(ITEM_TYPE_TASK)
    case t if t.equalsIgnoreCase(ITEM_TYPE_TAG_NAME.toLowerCase) => Some(ITEM_TYPE_TAG)
    case _ => None
  }

  def getActionName(action:Int) : Option[String] = action match {
    case ACTION_TYPE_UPDATED => Some(ACTION_TYPE_UPDATED_NAME)
    case ACTION_TYPE_CREATED => Some(ACTION_TYPE_CREATED_NAME)
    case ACTION_TYPE_DELETED => Some(ACTION_TYPE_DELETED_NAME)
    case ACTION_TYPE_TASK_VIEWED => Some(ACTION_TYPE_TASK_VIEWED_NAME)
    case ACTION_TYPE_TASK_STATUS_SET => Some(ACTION_TYPE_TASK_STATUS_SET_NAME)
    case ACTION_TYPE_TAG_ADDED => Some(ACTION_TYPE_TAG_ADDED_NAME)
    case ACTION_TYPE_TAG_REMOVED => Some(ACTION_TYPE_TAG_REMOVED_NAME)
    case _ => None
  }

  def getActionID(action:String) : Option[Int] = action.toLowerCase match {
    case t if t.equalsIgnoreCase(ACTION_TYPE_UPDATED_NAME.toLowerCase) => Some(ACTION_TYPE_UPDATED)
    case t if t.equalsIgnoreCase(ACTION_TYPE_CREATED_NAME.toLowerCase) => Some(ACTION_TYPE_CREATED)
    case t if t.equalsIgnoreCase(ACTION_TYPE_DELETED_NAME.toLowerCase) => Some(ACTION_TYPE_DELETED)
    case t if t.equalsIgnoreCase(ACTION_TYPE_TASK_VIEWED_NAME.toLowerCase) => Some(ACTION_TYPE_TASK_VIEWED)
    case t if t.equalsIgnoreCase(ACTION_TYPE_TASK_STATUS_SET_NAME.toLowerCase) => Some(ACTION_TYPE_TASK_STATUS_SET)
    case t if t.equalsIgnoreCase(ACTION_TYPE_TAG_ADDED_NAME.toLowerCase) => Some(ACTION_TYPE_TAG_ADDED)
    case t if t.equalsIgnoreCase(ACTION_TYPE_TAG_REMOVED_NAME.toLowerCase) => Some(ACTION_TYPE_TAG_REMOVED)
    case _ => None
  }
}
