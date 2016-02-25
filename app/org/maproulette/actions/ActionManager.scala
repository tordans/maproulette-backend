package org.maproulette.actions

import anorm._
import play.api.Logger
import play.api.db.DB
import play.api.Play.current

/**
  * @author cuthbertm
  */
object ActionManager {

  // Columns
  val UserId = 0
  val typeId = 1
  val ItemId = 2
  val action = 3
  val status = 4
  // timeframe
  val HOUR = 0
  val DAY = 1
  val WEEK = 2
  val MONTH = 3
  val YEAR = 4

  private lazy val actionLevel = current.configuration.getInt("maproulette.action.level") match {
    case Some(level) => level
    case None => Actions.ACTION_LEVEL_2 // we default to action level 2
  }

  def setAction(userId:Int, item:Item with ItemType, action:ActionType, extra:String) = {
    if (action.getLevel > actionLevel) {
      Logger.trace("Action not logged, action level higher than threshold in configuration.")
    } else {
      DB.withTransaction { implicit c =>
        val statusId = action match {
          case t:TaskStatusSet => t.status
          case _ => 0
        }
        SQL"""INSERT INTO actions (user_id, type_id, item_id, action, status, extra)
                VALUES ($userId, ${item.typeId}, ${item.itemId}, ${action.getId},
                          $statusId, $extra)""".execute()
      }
    }
  }

  private def getActionSummary(columns:List[Int]=List.empty, timeframe:Option[Int]=None) = {
    SQL"""SELECT COUNT(*) as count, user_id, type_id, item_id, action FROM actions
         GROUP BY user_id, type_id, item_id, action"""
  }
}
