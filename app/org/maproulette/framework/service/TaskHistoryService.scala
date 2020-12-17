/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.service

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration
import org.joda.time.DateTime

import org.maproulette.framework.model.TaskLogEntry
import org.maproulette.framework.psql.{Query, _}
import org.maproulette.framework.psql.filter.{BaseParameter, _}
import org.maproulette.framework.repository.TaskHistoryRepository
import org.maproulette.session.SearchParameters
import org.maproulette.permissions.Permission
import org.maproulette.data.Actions
import org.maproulette.provider.websockets.{WebSocketMessages, WebSocketProvider}

/**
  * Service layer for TaskHistory
  *
  * @author krotstan
  */
@Singleton
class TaskHistoryService @Inject() (
    repository: TaskHistoryRepository,
    serviceManager: ServiceManager,
    permission: Permission,
    webSocketProvider: WebSocketProvider
) {

  /**
    * Returns a history log for the task -- includes comments, status actions,
    * review actions
    * @param taskId
    * @return List of TaskLogEntry
    */
  def getTaskHistoryLog(taskId: Long): List[TaskLogEntry] = {
    val comments      = repository.getComments(taskId)
    val reviews       = repository.getReviews(taskId)
    val statusActions = repository.getStatusActions(taskId)
    val actions       = repository.getActions(taskId, Actions.ACTION_TYPE_UPDATED)

    ((comments ++ reviews ++ statusActions ++ actions).sortWith(sortByDate)).reverse
  }

  private def sortByDate(entry1: TaskLogEntry, entry2: TaskLogEntry) = {
    entry1.timestamp.getMillis() < entry2.timestamp.getMillis()
  }
}
