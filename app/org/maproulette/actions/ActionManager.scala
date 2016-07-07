// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.actions

import java.sql.Timestamp
import javax.inject.{Inject, Singleton}

import anorm.SqlParser._
import anorm._
import org.joda.time.DateTime
import org.maproulette.Config
import org.maproulette.session.User
import play.api.{Application, Logger}
import play.api.db.Database

import scala.collection.mutable.ListBuffer
import org.maproulette.models.Task
import org.maproulette.models.utils.DALHelper
import play.api.libs.json.{Json, Reads, Writes}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

/**
  * This file handles retrieving the action summaries from the database. This primarily revolves
  * around how task status' have changed, but would also include creation, updating and deletion
  * of any of the objects in the system. Is primarily used for statistics.
  *
  * @author cuthbertm
  */
case class ActionItem(created:Option[DateTime]=None,
                      osmUserId:Option[Long]=None,
                      typeId:Option[Int]=None,
                      itemId:Option[Long]=None,
                      action:Option[Int]=None,
                      status:Option[Int]=None,
                      extra:Option[String]=None)

/**
  * @param columns The columns that you want returned limited to UserId = 0, typeId = 1, itemId = 2, action = 3 and status = 4
  * @param timeframe You can specify whether you want the data returned per Hour = 0, Day = 1, Week = 2, Month = 3 and Year = 4
  * @param osmUserLimit Filter the query based on a set of user id's
  * @param typeLimit Filter the query based on the type of item Project = 0, Challenge = 1, Task = 2
  * @param itemLimit Filter the query based on a set of item id's
  * @param statusLimit Filter the query based on status Created = 0, Fixed = 1, FalsePositive = 2, Skipped = 3, Deleted = 4
  * @param actionLimit Filter based on the specific actions
  * @param startTime Filter where created time for action is greater than the provided start time
  * @param endTime Filter where created time for action is less than the provided end time
  */
case class ActionLimits(columns:List[Int]=List.empty,
                        timeframe:Option[Int]=None,
                        osmUserLimit:List[Long]=List.empty,
                        typeLimit:List[Int]=List.empty,
                        itemLimit:List[Long]=List.empty,
                        statusLimit:List[Int]=List.empty,
                        actionLimit:List[Int]=List.empty,
                        startTime:Option[Timestamp]=None,
                        endTime:Option[Timestamp]=None)

@Singleton
class ActionManager @Inject()(config: Config, db:Database)(implicit application:Application) extends DALHelper {

  // Columns
  val userId = 0
  val typeId = 1
  val itemId = 2
  val action = 3
  val status = 4
  // timeframe
  val HOUR = 0
  val DAY = 1
  val WEEK = 2
  val MONTH = 3
  val YEAR = 4

  implicit val actionItemWrites: Writes[ActionItem] = Json.writes[ActionItem]
  implicit val actionItemReads: Reads[ActionItem] = Json.reads[ActionItem]

  /**
    * A anorm row parser for the actions table
    */
  implicit val parser: RowParser[ActionItem] = {
      get[Option[DateTime]]("created") ~
      get[Option[Long]]("actions.osm_user_id") ~
      get[Option[Int]]("actions.type_id") ~
      get[Option[Long]]("actions.item_id") ~
      get[Option[Int]]("actions.action") ~
      get[Option[Int]]("actions.status") ~
      get[Option[String]]("actions.extra") map {
      case created ~ osmUserId ~ typeId ~ itemId ~ action ~ status ~ extra => {
        new ActionItem(created, osmUserId, typeId, itemId, action, status, extra)
      }
    }
  }

  /**
    * Creates an action in the database
    *
    * @param user The user executing the request
    * @param item The item that the action was performed on
    * @param action The action that was performed
    * @param extra And extra information that you want to send along with the creation of the action
    * @return true if created
    */
  def setAction(user:Option[User]=None, item:Item with ItemType, action:ActionType, extra:String) : Future[Boolean] = {
    Future {
      if (action.getLevel > config.actionLevel) {
        Logger.trace("Action not logged, action level higher than threshold in configuration.")
        false
      } else {
        db.withTransaction { implicit c =>
          val statusId = action match {
            case t:TaskStatusSet => t.status
            case _ => 0
          }
          val userId = user match {
            case Some(u) => Some(u.osmProfile.id)
            case None => None
          }
          SQL"""INSERT INTO actions (osm_user_id, type_id, item_id, action, status, extra)
                VALUES ($userId, ${item.typeId}, ${item.itemId}, ${action.getId},
                          $statusId, $extra)""".execute()
        }
      }
    }
  }

  /**
    * The status action is set in a different table for performance and efficiency reasons
    *
    * @param user The user set the task status
    * @param task The task that is having it's status set
    * @param status The new updated status that was replaced
    * @return
    */
  def setStatusAction(user:User, task:Task, status:Int) : Future[Boolean] = {
    Future {
      db.withTransaction { implicit c =>
        SQL"""INSERT INTO status_actions (osm_user_id, project_id, challenge_id, task_id, old_status, status)
                SELECT ${user.osmProfile.id}, parent_id, ${task.parent}, ${task.id}, ${task.status}, $status
                FROM challenges WHERE id = ${task.parent}
          """.execute()
      }
    }
  }

  /**
    * Helper function for getActivity list that gets the recent activity for a specific user, and
    * will only retrieve activity where status was set for tasks.
    *
    * @param user The user to get the activity for
    * @param limit limit the number of returned items
    * @param offset paging, starting at 0
    * @return
    */
  def getRecentActivity(user:User, limit:Int=Config.DEFAULT_LIST_SIZE, offset:Int=0) : List[ActionItem] =
    getActivityList(limit, offset,
      ActionLimits(osmUserLimit = List(user.osmProfile.id),
        actionLimit = List(Actions.ACTION_TYPE_TASK_STATUS_SET,
          Actions.ACTION_TYPE_QUESTION_ANSWERED)))

  /**
    * Gets the activity list from the actions table
    *
    * @param limit The number of activities to list
    * @param offset the paging starting at 0
    * @param actionLimits Not all action limits are used. Here only typeQuery, itemQuery and actionLimit are used
    * @return
    */
  def getActivityList(limit:Int=Config.DEFAULT_LIST_SIZE, offset:Int=0, actionLimits: ActionLimits) : List[ActionItem] = {
    val parameters = new ListBuffer[NamedParameter]()
    val whereClause = new StringBuilder()
    val typeQuery = if (actionLimits.typeLimit.nonEmpty) {
      parameters += ('typeIds -> actionLimits.typeLimit)
      whereClause ++= "type_id IN ({typeIds})"
    }

    val itemQuery = if (actionLimits.itemLimit.nonEmpty) {
      parameters += ('itemIds -> actionLimits.itemLimit)
      if (whereClause.nonEmpty) {
        whereClause ++= " AND "
      }
      whereClause ++= "item_id IN ({itemIds})"
    }

    val actionQuery = if (actionLimits.actionLimit.nonEmpty) {
      parameters += ('actions -> actionLimits.actionLimit)
      if (whereClause.nonEmpty) {
        whereClause ++= " AND "
      }
      whereClause ++= "action IN ({actions})"
    }

    val userQuery = if (actionLimits.osmUserLimit.nonEmpty) {
      parameters += ('userIds -> actionLimits.osmUserLimit)
      if (whereClause.nonEmpty) {
        whereClause ++= " AND "
      }
      whereClause ++= "osm_user_id IN ({userIds})"
    }

    val query =
      s"""SELECT * FROM actions
         | ${if (whereClause.nonEmpty) { s"WHERE ${whereClause.toString}" }}
         |ORDER BY CREATED DESC
         |LIMIT ${sqlLimit(limit)} OFFSET $offset""".stripMargin
    db.withConnection { implicit c =>
      sqlWithParameters(query, parameters).as(parser.*)
    }
  }
}
