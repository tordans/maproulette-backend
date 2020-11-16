/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.repository

import java.sql.Connection
import scala.concurrent.duration.FiniteDuration

import anorm.ToParameterValue
import anorm.{RowParser, ~}
import anorm._, postgresql._
import anorm.SqlParser.{get, int, long, str}
import javax.inject.{Inject, Singleton}
import org.maproulette.session.SearchParameters
import org.maproulette.framework.psql.Query
import org.maproulette.framework.psql.filter.{BaseParameter, CustomParameter, Parameter}
import org.maproulette.models.{Task, TaskCluster, Point}
import org.maproulette.framework.model.User
import play.api.db.Database
import play.api.libs.json._

@Singleton
class TaskClusterRepository @Inject() (override val db: Database) extends RepositoryMixin {
  implicit val baseTable: String = "tasks"

  val DEFAULT_NUMBER_OF_POINTS = 100

  /**
    * Queries task clusters with the give query filters and number of points
    *
    * @param query - Query with the built in filters
    * @param numberOfPoints - Number of points to return
    * @param params - SearchParameters to save with the search
    */
  def queryTaskClusters(
      query: Query,
      numberOfPoints: Int,
      params: SearchParameters
  ): List[TaskCluster] = {
    val joinClause =
      """
        LEFT JOIN locked l ON l.item_id = tasks.id
        INNER JOIN challenges c ON c.id = tasks.parent_id
        INNER JOIN projects p ON p.id = c.parent_id
        LEFT OUTER JOIN task_review ON task_review.task_id = tasks.id
      """

    this.withMRTransaction { implicit c =>
      val (sql, parameters) = this.getTaskClusterQuery(query, joinClause, numberOfPoints)
      val result            = SQL(sql).on(parameters: _*).as(this.getTaskClusterParser(params).*)

      // Filter out invalid clusters.
      result.filter(_ != None).asInstanceOf[List[TaskCluster]]
    }
  }

  def getTaskClusterQuery(
      query: Query,
      joinClause: String,
      numberOfPoints: Int
  ): (String, List[NamedParameter]) = {
    return (
      s"""SELECT kmeans, count(*) as numberOfPoints,
            CASE WHEN count(*)=1 THEN (array_agg(taskId))[1] END as taskId,
            CASE WHEN count(*)=1 THEN (array_agg(geojson))[1] END as geojson,
            CASE WHEN count(*)=1 THEN (array_agg(taskStatus))[1] END as taskStatus,
            CASE WHEN count(*)=1 THEN (array_agg(taskPriority))[1] END as taskPriority,
            ST_AsGeoJSON(ST_Centroid(ST_Collect(location))) AS geom,
            ST_AsGeoJSON(ST_ConvexHull(ST_Collect(location))) AS bounding,
            array_agg(distinct challengeIds) as challengeIds
         FROM (
           SELECT tasks.id as taskId, tasks.status as taskStatus, tasks.priority as taskPriority, tasks.geojson::TEXT as geojson, ST_ClusterKMeans(tasks.location,
                      (SELECT
                          CASE WHEN COUNT(*) < $numberOfPoints THEN COUNT(*) ELSE $numberOfPoints END
                        FROM tasks
                        $joinClause
                        WHERE ${query.filter.sql()}
                      )::Integer
                    ) OVER () AS kmeans, tasks.location, tasks.parent_id as challengeIds
           FROM tasks
           $joinClause
           WHERE ${query.filter.sql()}
         ) AS ksub
         WHERE location IS NOT NULL
         GROUP BY kmeans
         ORDER BY kmeans
      """,
      query.parameters()
    )
  }

  def getTaskClusterParser(params: SearchParameters): anorm.RowParser[Serializable] = {
    int("kmeans") ~ int("numberOfPoints") ~ get[Option[Long]]("taskId") ~
      get[Option[Int]]("taskStatus") ~ get[Option[Int]]("taskPriority") ~ get[Option[String]](
      "geojson"
    ) ~ str("geom") ~
      str("bounding") ~ get[List[Long]]("challengeIds") map {
      case kmeans ~ totalPoints ~ taskId ~ taskStatus ~ taskPriority ~ geojson ~ geom ~ bounding ~ challengeIds =>
        val locationJSON = Json.parse(geom)
        val coordinates  = (locationJSON \ "coordinates").as[List[Double]]
        // Let's check to make sure we received valid number of coordinates.
        if (coordinates.length > 1) {
          val point = Point(coordinates(1), coordinates.head)
          TaskCluster(
            kmeans,
            totalPoints,
            taskId,
            taskStatus,
            taskPriority,
            params,
            point,
            Json.parse(bounding),
            challengeIds,
            geojson.map(Json.parse(_))
          )
        } else {
          None
        }
    }
  }
}
