/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.repository

import java.sql.Connection
import scala.concurrent.duration.FiniteDuration

import anorm.SqlParser.get
import anorm.ToParameterValue
import anorm.{RowParser, ~}
import javax.inject.{Inject, Singleton}
import org.maproulette.framework.psql.Query
import org.maproulette.models.Task
import org.maproulette.framework.model.User
import play.api.db.Database
import play.api.libs.json.JsValue
@Singleton
class TaskRepository @Inject() (override val db: Database) extends RepositoryMixin {
  implicit val baseTable: String = "tasks"

  /**
    * Updates the completionResponses on a Task
    *
    * @param task  The task to update
    * @param user  The user making the request
    * @param completionResponses json responses provided by user to task instruction questions
    */
  def updateCompletionResponses(task: Task, user: User, completionResponses: JsValue): Unit = {
    this.withMRTransaction { implicit c =>
      val query = Query.simple(List())

      query
        .build(s"""UPDATE tasks t SET completion_responses = {responses}::JSONB
              WHERE t.id = (
                SELECT t2.id FROM tasks t2
                LEFT JOIN locked l on l.item_id = t2.id AND l.item_type = {itemType}
                WHERE t2.id = {taskId} AND (l.user_id = {userId} OR l.user_id IS NULL)
              )""")
        .on(
          Symbol("responses") -> ToParameterValue
            .apply[String]
            .apply(completionResponses.toString),
          Symbol("itemType") -> ToParameterValue
            .apply[Int]
            .apply(task.itemType.typeId),
          Symbol("taskId") -> ToParameterValue
            .apply[Long]
            .apply(task.id),
          Symbol("userId") -> ToParameterValue
            .apply[Long]
            .apply(user.id)
        )
        .executeUpdate()

    }
  }
}
