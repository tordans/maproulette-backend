/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package org.maproulette.models.dal.mixin

import anorm.NamedParameter
import org.maproulette.models.utils.DALHelper
import org.maproulette.session.SearchParameters
import org.maproulette.framework.psql.SQLUtils
import play.api.libs.json.JsDefined

import scala.collection.mutable.ListBuffer

/**
  * NOTE: This class has quite a few side effects that need to be taken into account. Specifically
  * that the "whereClause" and "joinClause" are updated through the string builder functions
  * and not returned. So basically functioning like InOut Parameters. Not the best approach
  *
  * @author mcuthbert
  */
@deprecated
trait SearchParametersMixin extends DALHelper {

  def updateWhereClause(
      params: SearchParameters,
      whereClause: StringBuilder,
      joinClause: StringBuilder
  )(implicit projectSearch: Boolean = true): ListBuffer[NamedParameter] = {
    val parameters = new ListBuffer[NamedParameter]()

    this.paramsLocation(params, whereClause)
    this.paramsBounding(params, whereClause)
    this.paramsTaskStatus(params, whereClause)
    this.paramsTaskId(params, whereClause)
    this.paramsProjectSearch(params, whereClause)
    this.paramsTaskReviewStatus(params, whereClause)
    this.paramsOwner(params, whereClause)
    this.paramsReviewer(params, whereClause)
    this.paramsMapper(params, whereClause)
    this.paramsTaskPriorities(params, whereClause)
    this.paramsTaskTags(params, whereClause)
    this.paramsPriority(params, whereClause)
    this.paramsChallengeDifficulty(params, whereClause)
    this.paramsChallengeStatus(params, whereClause)
    this.paramsChallengeRequiresLocal(params, whereClause)
    this.paramsBoundingGeometries(params, whereClause)

    // For efficiency can only query on task properties with a parent challenge id
    this.paramsTaskProps(params, whereClause)

    parameters ++= this.addSearchToQuery(params, whereClause)(projectSearch)
    parameters ++= this.addChallengeTagMatchingToQuery(params, whereClause, joinClause)
    parameters
  }

  def paramsProjectSearch(params: SearchParameters, whereClause: StringBuilder): Unit = {
    params.projectSearch match {
      case Some(ps) =>
        val invert      = if (params.invertFields.getOrElse(List()).contains("ps")) "NOT" else ""
        val projectName = ps.replace("'", "''")
        this.appendInWhereClause(
          whereClause,
          s"""LOWER(p.display_name) ${invert} LIKE LOWER('%${projectName}%')"""
        )
      case None => // do nothing
    }
  }

  def paramsLocation(params: SearchParameters, whereClause: StringBuilder): Unit = {
    params.location match {
      case Some(sl) =>
        this.appendInWhereClause(
          whereClause,
          s"tasks.location @ ST_MakeEnvelope (${sl.left}, ${sl.bottom}, ${sl.right}, ${sl.top}, 4326)"
        )
      case None => // do nothing
    }
  }

  def paramsBounding(params: SearchParameters, whereClause: StringBuilder): Unit = {
    params.bounding match {
      case Some(sl) =>
        this.appendInWhereClause(
          whereClause,
          s"c.bounding @ ST_MakeEnvelope (${sl.left}, ${sl.bottom}, ${sl.right}, ${sl.top}, 4326)"
        )
      case None => // do nothing
    }
  }

  def paramsTaskStatus(params: SearchParameters, whereClause: StringBuilder): Unit = {
    params.taskParams.taskStatus match {
      case Some(sl) if sl.nonEmpty =>
        val invert = if (params.invertFields.getOrElse(List()).contains("tStatus")) "NOT" else ""

        // If list contains -1 then ignore filtering by status
        if (!sl.contains(-1)) {
          this.appendInWhereClause(whereClause, s"tasks.status ${invert} IN (${sl.mkString(",")})")
        }
      case Some(sl) if sl.isEmpty => //ignore this scenario
      case _ =>
        this.appendInWhereClause(whereClause, "tasks.status IN (0,3,6)")
    }
  }

  def paramsTaskId(params: SearchParameters, whereClause: StringBuilder): Unit = {
    params.taskParams.taskId match {
      case Some(tid) =>
        this.appendInWhereClause(whereClause, s"CAST(tasks.id AS TEXT) LIKE '${tid}%'")
      case _ => // do nothing
    }
  }

  def paramsTaskPriorities(params: SearchParameters, whereClause: StringBuilder): Unit = {
    params.taskParams.taskPriorities match {
      case Some(sl) if sl.nonEmpty =>
        val invert = if (params.invertFields.getOrElse(List()).contains("priorities")) "NOT" else ""
        this.appendInWhereClause(whereClause, s"tasks.priority ${invert} IN (${sl.mkString(",")})")
      case Some(sl) if sl.isEmpty => //ignore this scenario
      case _                      => // do nothing
    }
  }

  def paramsPriority(params: SearchParameters, whereClause: StringBuilder): Unit = {
    params.priority match {
      case Some(p) if p == 0 || p == 1 || p == 2 =>
        val invert = if (params.invertFields.getOrElse(List()).contains("tp")) "!" else ""
        this.appendInWhereClause(whereClause, s"tasks.priority ${invert}= $p")
      case _ => // ignore
    }
  }

  def paramsTaskTags(params: SearchParameters, whereClause: StringBuilder): Unit = {
    if (params.hasTaskTags) {
      val tagList = params.taskParams.taskTags.get
        .map(t => {
          SQLUtils.testColumnName(t)
          s"'${t}'"
        })
        .mkString(",")
      this.appendInWhereClause(
        whereClause,
        s"""tasks.id IN (SELECT task_id from tags_on_tasks tt
                         INNER JOIN tags ON tags.id = tt.tag_id
                         WHERE tags.name IN (${tagList}))
        """
      )
    }
  }

  def paramsChallengeDifficulty(params: SearchParameters, whereClause: StringBuilder): Unit = {
    params.challengeParams.challengeDifficulty match {
      case Some(v) if v > 0 && v < 4 =>
        this.appendInWhereClause(whereClause, s"c.difficulty = ${v}")
      case _ =>
    }
  }

  def paramsTaskReviewStatus(
      params: SearchParameters,
      whereClause: StringBuilder
  ): Unit = {
    params.taskParams.taskReviewStatus match {
      case Some(statuses) if statuses.nonEmpty =>
        val invert = if (params.invertFields.getOrElse(List()).contains("trStatus")) "NOT" else ""
        val filter = new StringBuilder(s"""(tasks.id ${invert} IN (SELECT task_id FROM task_review
                                                    WHERE task_review.task_id = tasks.id AND task_review.review_status
                                                          IN (${statuses.mkString(",")})) """)
        if (statuses.contains(-1)) {
          val includeNOT =
            if (!params.invertFields.getOrElse(List()).contains("trStatus")) "NOT" else ""
          filter.append(
            s" OR tasks.id ${includeNOT} IN (SELECT task_id FROM task_review task_review WHERE task_review.task_id = tasks.id)"
          )
        }
        filter.append(")")
        this.appendInWhereClause(whereClause, filter.toString())
      case Some(statuses) if statuses.isEmpty => //ignore this scenario
      case _                                  =>
    }
  }

  def paramsChallengeStatus(
      params: SearchParameters,
      whereClause: StringBuilder
  ): Unit = {
    params.challengeParams.challengeStatus match {
      case Some(sl) if sl.nonEmpty =>
        val statusClause = new StringBuilder(s"(c.status IN (${sl.mkString(",")})")
        if (sl.contains(-1)) {
          statusClause ++= " OR c.status IS NULL"
        }
        statusClause ++= ")"
        this.appendInWhereClause(whereClause, statusClause.toString())
      case Some(sl) if sl.isEmpty => //ignore this scenario
      case _                      =>
    }
  }

  def paramsChallengeRequiresLocal(
      params: SearchParameters,
      whereClause: StringBuilder
  ): Unit = {
    params.challengeParams.challengeIds match {
      case Some(ids) if ids.nonEmpty =>
      // do nothing, we don't want to restrict to requiresLocal if we have
      // specific challenge ids
      case _ =>
        params.challengeParams.requiresLocal match {
          case Some(SearchParameters.CHALLENGE_REQUIRES_LOCAL_EXCLUDE) =>
            this.appendInWhereClause(whereClause, s"c.requires_local = false")
          case Some(SearchParameters.CHALLENGE_REQUIRES_LOCAL_ONLY) =>
            this.appendInWhereClause(whereClause, s"c.requires_local = true")
          case _ =>
          // allow everything
        }
    }
  }

  def paramsBoundingGeometries(params: SearchParameters, whereClause: StringBuilder): Unit = {
    params.boundingGeometries match {
      case Some(bp) =>
        val allPolygons = new StringBuilder()
        bp.foreach(value =>
          value \ "bounding" match {
            case locationJSON: JsDefined => {
              val polygonLinestring = new StringBuilder()
              if (allPolygons.toString() != "")
                allPolygons.append(" OR ")

              (locationJSON \ "type").as[String] match {
                case "Polygon" => {
                  (locationJSON \ "coordinates")
                    .as[List[List[List[Double]]]]
                    .foreach(p =>
                      p.foreach(coordinates => {
                        if (polygonLinestring.toString() != "")
                          polygonLinestring.append(",")
                        polygonLinestring.append(s"${coordinates.head} ${coordinates(1)}")
                      })
                    )
                  allPolygons.append(
                    s"tasks.location @ ST_MakePolygon( ST_GeomFromText('LINESTRING($polygonLinestring)'))"
                  )
                }
                case "LineString" => {
                  (locationJSON \ "coordinates")
                    .as[List[List[Double]]]
                    .foreach(coordinates => {
                      if (polygonLinestring.toString() != "") {
                        polygonLinestring.append(",")
                      }
                      polygonLinestring.append(s"${coordinates.head} ${coordinates(1)}")
                    })
                  allPolygons.append(
                    s"tasks.location @ ST_GeomFromText('LINESTRING($polygonLinestring)')"
                  )
                }
                case "Point" => {
                  val coordinates = (locationJSON \ "coordinates").as[List[Double]]
                  allPolygons.append(
                    s"tasks.location @ ST_GeomFromText('POINT(${coordinates.head} ${coordinates(1)})')"
                  )
                }
                case _ => // do nothing
              }
            }
            case _ => // do nothing
          }
        )
        this.appendInWhereClause(whereClause, "(" + allPolygons.toString + ")")
      case _ => // value not present
    }
  }

  def paramsTaskProps(
      params: SearchParameters,
      whereClause: StringBuilder
  ): Unit = {
    params.getChallengeIds match {
      case Some(l) =>
        params.taskParams.taskPropertySearch match {
          case Some(tps) =>
            val query = new StringBuilder(s"""tasks.id IN (
                SELECT id FROM tasks,
                               jsonb_array_elements(geojson->'features') features
                WHERE parent_id IN (${l.mkString(",")})
                AND (${tps.toSQL}))
               """)
            this.appendInWhereClause(whereClause, query.toString())
          case _ =>
            params.taskParams.taskProperties match {
              case Some(tp) =>
                val searchType = params.taskParams.taskPropertySearchType.getOrElse("equals")

                val query = new StringBuilder(s"""tasks.id IN (
                    SELECT id FROM tasks,
                                   jsonb_array_elements(geojson->'features') features
                    WHERE parent_id IN (${l.mkString(",")})
                    AND (true
                   """)
                for ((k, v) <- tp) {
                  searchType match {
                    case SearchParameters.TASK_PROP_SEARCH_TYPE_EQUALS =>
                      query ++= s" AND features->'properties'->>'${k}' = '${v}' "
                    case SearchParameters.TASK_PROP_SEARCH_TYPE_NOT_EQUAL =>
                      query ++= s" AND features->'properties'->>'${k}' != '${v}' "
                    case SearchParameters.TASK_PROP_SEARCH_TYPE_CONTAINS =>
                      query ++= s" AND features->'properties'->>'${k}' LIKE '%${v}%' "
                    case SearchParameters.TASK_PROP_SEARCH_TYPE_EXISTS =>
                      query ++= s" AND features->'properties'->>'${k}' IS NOT NULL "
                    case SearchParameters.TASK_PROP_SEARCH_TYPE_MISSING =>
                      query ++= s" AND features->'properties'->>'${k}' IS NULL "
                    case _ => // should not happen
                  }
                }
                query ++= "))"
                this.appendInWhereClause(whereClause, query.toString())
              case _ =>
            }
        }
      case None => // ignore
    }
  }

  def paramsOwner(
      params: SearchParameters,
      whereClause: StringBuilder
  ): Unit = {
    params.owner match {
      case Some(o) if o.nonEmpty =>
        val invert = if (params.invertFields.getOrElse(List()).contains("o")) "NOT" else ""
        this.appendInWhereClause(
          whereClause,
          s""" (tasks.id ${invert} IN (SELECT task_id
                                       FROM task_review tr
                                       INNER JOIN users u ON u.id = tr.review_requested_by
                                       WHERE tr.task_id = tasks.id AND
                                             LOWER(u.name) LIKE LOWER('%${o}%') ))
           """
        )
      case _ => // ignore
    }
  }

  def paramsReviewer(
      params: SearchParameters,
      whereClause: StringBuilder
  ): Unit = {
    params.reviewer match {
      case Some(r) if r.nonEmpty =>
        val invert = if (params.invertFields.getOrElse(List()).contains("r")) "NOT" else ""
        this.appendInWhereClause(
          whereClause,
          s""" (tasks.id ${invert} IN (SELECT task_id
                                       FROM task_review tr
                                       INNER JOIN users u ON u.id = tr.reviewed_by
                                       WHERE tr.task_id = tasks.id AND
                                             LOWER(u.name) LIKE LOWER('%${r}%') ))
           """
        )
      case _ => // ignore
    }
  }

  def paramsMapper(
      params: SearchParameters,
      whereClause: StringBuilder
  ): Unit = {
    params.mapper match {
      case Some(o) if o.nonEmpty =>
        val invert = if (params.invertFields.getOrElse(List()).contains("m")) "NOT" else ""
        this.appendInWhereClause(
          whereClause,
          s""" (tasks.id ${invert} IN (SELECT t2.id
                                       FROM tasks t2
                                       INNER JOIN users u ON u.id = t2.completed_by
                                       WHERE t2.id = tasks.id AND
                                             LOWER(u.name) LIKE LOWER('%${o}%') ))
           """
        )
      case _ => // ignore
    }
  }

  def paramsMappers(
      params: SearchParameters,
      whereClause: StringBuilder
  ): Unit = {
    params.reviewParams.mappers match {
      case Some(m) =>
        if (m.size > 0) {
          this.appendInWhereClause(
            whereClause,
            s"task_review.review_requested_by IN (${m.mkString(",")}) "
          )
        } else {
          this.appendInWhereClause(whereClause, s"task_review.review_requested_by IS NOT NULL ")
        }
      case _ =>
        this.appendInWhereClause(whereClause, s"task_review.review_requested_by IS NOT NULL ")
    }
  }

  def paramsReviewers(
      params: SearchParameters,
      whereClause: StringBuilder
  ): Unit = {
    params.reviewParams.reviewers match {
      case Some(r) =>
        if (r.size > 0) {
          this.appendInWhereClause(whereClause, s"task_review.reviewed_by IN (${r.mkString(",")}) ")
        }
      case _ => // do nothing
    }
  }

  def paramsPriorities(
      params: SearchParameters,
      whereClause: StringBuilder
  ): Unit = {
    params.reviewParams.priorities match {
      case Some(priority) if priority.nonEmpty =>
        val invert = if (params.invertFields.getOrElse(List()).contains("priorities")) "NOT" else ""
        val priorityClause = new StringBuilder(
          s"(tasks.priority ${invert} IN (${priority.mkString(",")}))"
        )
        this.appendInWhereClause(whereClause, priorityClause.toString())
      case Some(priority) if priority.isEmpty => //ignore this scenario
      case _                                  =>
    }
  }
}
