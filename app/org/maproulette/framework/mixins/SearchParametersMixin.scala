/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package org.maproulette.framework.mixins

import anorm.NamedParameter
import org.maproulette.session.{SearchParameters, SearchLocation}
import org.maproulette.framework.psql.SQLUtils
import org.maproulette.framework.psql.filter._
import org.maproulette.framework.psql.Query
import org.maproulette.framework.psql.{AND, OR}
import play.api.libs.json.JsDefined

import scala.collection.mutable.ListBuffer

trait SearchParametersMixin {

  /**
    * Filters by p.display_name with a like %projectSearch%
    * @param params with inverting on 'ps'
    */
  def filterProjectSearch(params: SearchParameters): Parameter[String] = {
    FilterParameter.conditional(
      "display_name",
      s"'${SQLUtils.search(params.projectSearch.getOrElse("").replace("'", "''"))}'",
      Operator.LIKE,
      params.invertFields.getOrElse(List()).contains("ps"),
      true,
      params.projectSearch != None,
      Some("p")
    )
  }

  /**
    * Filters by tasks.location with a @ MAKE_ENVELOPE etc.
    * @param params with inverting on 'tbb'
    */
  def filterLocation(params: SearchParameters): Parameter[String] = {
    this.locationSearch(
      "location",
      params.location,
      "tasks",
      params.invertFields.getOrElse(List()).contains("tbb")
    )
  }

  /**
    * Filters by c.bounding with a @ MAKE_ENVELOPE etc.
    * @param params with inverting on 'bb'
    */
  def filterBounding(params: SearchParameters): Parameter[String] = {
    this.locationSearch(
      "bounding",
      params.bounding,
      "c",
      params.invertFields.getOrElse(List()).contains("bb")
    )
  }

  /**
    * Filters by tasks.status
    * @param params with inverting on 'tStatus'
    * @param defaultStatus - used when no params.taskStatus is given, if you want to use all statuses
    *                        in this case pass in List()
    */
  def filterTaskStatus(
      params: SearchParameters,
      defaultStatuses: List[Int] = List(0, 3, 6)
  ): Parameter[String] = {
    // Do not filter if taskStatus list has -1 or list is empty
    val dontFilter =
      params.taskParams.taskStatus.getOrElse(defaultStatuses).isEmpty ||
        params.taskParams.taskStatus.getOrElse(defaultStatuses).contains(-1)

    FilterParameter.conditional(
      "status",
      // If taskStatus is not present then default taskStatus to 0,3,6
      params.taskParams.taskStatus.getOrElse(defaultStatuses).mkString(","),
      Operator.IN,
      params.invertFields.getOrElse(List()).contains("tStatus"),
      true,
      !dontFilter,
      Some("tasks")
    )
  }

  /**
    * Filters by tasks.id
    * @param params with inverting on 'tid'
    */
  def filterTaskId(params: SearchParameters): Parameter[String] = {
    val invert = if (params.invertFields.getOrElse(List()).contains("tid")) "NOT " else ""

    params.taskParams.taskId match {
      case Some(tid) =>
        CustomParameter(s"${invert}CAST(tasks.id AS TEXT) LIKE '${tid}%'")
      case _ => CustomParameter("")
    }
  }

  /**
    * Filters by tasks.priority
    * @param params with inverting on 'priorities'
    */
  def filterTaskPriorities(params: SearchParameters): Parameter[String] = {
    FilterParameter.conditional(
      "priority",
      params.taskParams.taskPriorities.getOrElse(List()).mkString(","),
      Operator.IN,
      params.invertFields.getOrElse(List()).contains("priorities"),
      true,
      params.taskParams.taskPriorities != None &&
        !params.taskParams.taskPriorities.getOrElse(List()).isEmpty,
      Some("tasks")
    )
  }

  /**
    * Filters by tasks.priority
    * @param params with inverting on 'tp'
    */
  def filterPriority(params: SearchParameters): Parameter[String] = {
    FilterParameter.conditional(
      "priority",
      params.priority.getOrElse("").toString,
      Operator.EQ,
      params.invertFields.getOrElse(List()).contains("tp"),
      true,
      params.priority != None &&
        (params.priority.get == 0 || params.priority.get == 1 || params.priority.get == 2),
      Some("tasks")
    )
  }

  /**
    * Filters by task tags
    * @param params with inverting on 'tt'
    */
  def filterTaskTags(params: SearchParameters): Parameter[String] = {
    if (params.hasTaskTags) {
      val tagList = params.taskParams.taskTags.get
        .map(t => {
          SQLUtils.testColumnName(t)
          s"'${t}'"
        })
        .mkString(",")

      val invert = if (params.invertFields.getOrElse(List()).contains("tt")) "NOT " else ""

      CustomParameter(
        s"""${invert}tasks.id IN (SELECT task_id from tags_on_tasks tt
              | INNER JOIN tags ON tags.id = tt.tag_id
              | WHERE tags.name IN (${tagList}))
        """.stripMargin
      )
    } else {
      CustomParameter("")
    }
  }

  /**
    * Filters by task review status
    * @param params with inverting on 'trStatus'
    */
  def filterTaskReviewStatus(params: SearchParameters): Parameter[String] = {
    params.taskParams.taskReviewStatus match {
      case Some(statuses) if statuses.nonEmpty =>
        val invert = params.invertFields.getOrElse(List()).contains("trStatus")
        val filter = new StringBuilder(s"""${if (invert) "NOT " else ""}(tasks.id IN
              | (SELECT task_id FROM task_review
              | WHERE task_review.task_id = tasks.id AND task_review.review_status
              | IN (${statuses.mkString(",")}))""".stripMargin)
        if (statuses.contains(-1)) {
          filter.append(s""" OR tasks.id NOT IN (SELECT task_id FROM task_review task_review
              | WHERE task_review.task_id = tasks.id)""".stripMargin)
        }
        filter.append(")")
        CustomParameter(filter.toString())
      case Some(statuses) if statuses.isEmpty => CustomParameter("")
      case _                                  => CustomParameter("")
    }
  }

  /**
    * Filters by c.difficulty
    * @param params with inverting on 'cd'
    */
  def filterChallengeDifficulty(params: SearchParameters): Parameter[String] = {
    FilterParameter.conditional(
      "difficulty",
      params.challengeParams.challengeDifficulty.getOrElse("").toString,
      Operator.EQ,
      params.invertFields.getOrElse(List()).contains("cd"),
      true,
      params.challengeParams.challengeDifficulty != None,
      Some("c")
    )
  }

  /**
    * Filters by c.status
    * @param params with inverting on 'cStatus'
    */
  def filterChallengeStatus(params: SearchParameters): FilterGroup = {
    val searchList = params.challengeParams.challengeStatus.getOrElse(List())
    val invert     = params.invertFields.getOrElse(List()).contains("cStatus")

    FilterGroup(
      List(
        BaseParameter(
          "status",
          searchList.mkString(","),
          Operator.IN,
          invert,
          true,
          Some("c")
        ),
        FilterParameter.conditional(
          "status",
          None,
          Operator.NULL,
          invert,
          true,
          searchList.contains(-1),
          Some("c")
        )
      ),
      (if (invert) AND() else OR()),
      params.challengeParams.challengeStatus != None
    )
  }

  /**
    * Filters by c.requires_local
    */
  def filterChallengeRequiresLocal(params: SearchParameters): FilterGroup = {
    FilterGroup(
      List(
        params.challengeParams.challengeIds match {
          case Some(ids) if ids.nonEmpty => CustomParameter("")
          // do nothing, we don't want to restrict to requiresLocal if we have
          // specific challenge ids
          case _ =>
            params.challengeParams.requiresLocal match {
              case Some(SearchParameters.CHALLENGE_REQUIRES_LOCAL_EXCLUDE) =>
                BaseParameter(
                  "requires_local",
                  None,
                  Operator.BOOL,
                  true, // negate
                  table = Some("c")
                )
              case Some(SearchParameters.CHALLENGE_REQUIRES_LOCAL_ONLY) =>
                BaseParameter(
                  "requires_local",
                  None,
                  Operator.BOOL,
                  false, // don't negate
                  table = Some("c")
                )
              case _ => CustomParameter("")
            }
        }
      )
    )
  }

  /**
    * Filters on tasks.location @ GEOMETRY
    */
  def filterBoundingGeometries(params: SearchParameters): Parameter[String] = {
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
        CustomParameter("(" + allPolygons.toString + ")")
      case _ => CustomParameter("")
    }
  }

  /**
    * Filters on tasks features
    */
  def filterTaskProps(params: SearchParameters): Parameter[String] = {
    params.getChallengeIds match {
      case Some(l) =>
        params.taskParams.taskPropertySearch match {
          case Some(tps) =>
            val query = new StringBuilder(s"""tasks.id IN (
                | SELECT id FROM tasks,
                | jsonb_array_elements(geojson->'features') features
                | WHERE parent_id IN (${l.mkString(",")})
                | AND (${tps.toSQL}))""".stripMargin)
            CustomParameter(query.toString())
          case _ =>
            params.taskParams.taskProperties match {
              case Some(tp) =>
                val searchType = params.taskParams.taskPropertySearchType.getOrElse("equals")

                val query = new StringBuilder(s"""tasks.id IN (
                    | SELECT id FROM tasks,
                    | jsonb_array_elements(geojson->'features') features
                    | WHERE parent_id IN (${l.mkString(",")})
                    | AND (true""".stripMargin)
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
                CustomParameter(query.toString())
              case _ => CustomParameter("")
            }
        }
      case None => CustomParameter("")
    }
  }

  /**
    * Filters on tasks task_review.review_requested_by
    * @param params with inverting on 'o'
    */
  def filterOwner(params: SearchParameters): FilterGroup = {
    params.owner match {
      case Some(o) if o.nonEmpty =>
        this.buildReviewSubQuerySearch(params, "review_requested_by", o, "o")
      case _ => FilterGroup(List())
    }
  }

  /**
    * Filters on tasks task_review.reviewed_by
    * @param params with inverting on 'r'
    */
  def filterReviewer(params: SearchParameters): FilterGroup = {
    params.reviewer match {
      case Some(r) if r.nonEmpty =>
        this.buildReviewSubQuerySearch(params, "reviewed_by", r, "r")
      case _ => FilterGroup(List())
    }
  }

  /**
    * Filters on tasks.completedBy
    * @param params with inverting on 'm'
    */
  def filterMapper(params: SearchParameters): FilterGroup = {
    params.mapper match {
      case Some(m) if m.nonEmpty =>
        FilterGroup(
          List(
            SubQueryFilter(
              "id",
              Query.simple(
                List(
                  BaseParameter("t2.id", "tasks.id", useValueDirectly = true),
                  BaseParameter("u.name", s"'%${m}%'", Operator.ILIKE, useValueDirectly = true)
                ),
                "SELECT t2.id FROM tasks t2 INNER JOIN users u ON u.id = t2.completed_by"
              ),
              params.invertFields.getOrElse(List()).contains("m"),
              Operator.IN,
              Some("tasks")
            )
          )
        )
      case _ => FilterGroup(List())
    }
  }

  /**
    * Filters on task_review.review_requested_by.
    * Will always check that the field is not null
    *
    * @param params with inverting on 'mappers'
    */
  def filterReviewMappers(params: SearchParameters): FilterGroup = {
    FilterGroup(
      List(
        FilterParameter.conditional(
          "review_requested_by",
          params.reviewParams.mappers.getOrElse(List()).mkString(","),
          Operator.IN,
          params.invertFields.getOrElse(List()).contains("mappers"),
          true,
          !params.reviewParams.mappers.getOrElse(List()).isEmpty,
          Some("task_review")
        ),
        BaseParameter(
          "review_requested_by",
          None,
          Operator.NULL,
          true,
          true,
          Some("task_review")
        )
      )
    )
  }

  /**
    * Filters on task_review.reviewed_by.
    *
    * @param params with inverting on 'reviewers'
    */
  def filterReviewers(params: SearchParameters): Parameter[String] = {
    FilterParameter.conditional(
      "reviewed_by",
      params.reviewParams.reviewers.getOrElse(List()).mkString(","),
      Operator.IN,
      params.invertFields.getOrElse(List()).contains("reviewers"),
      true,
      !params.reviewParams.reviewers.getOrElse(List()).isEmpty,
      Some("task_review")
    )
  }

  /**
    * Private method to build a bounding box search
    */
  private def locationSearch(
      key: String,
      bounds: Option[SearchLocation],
      table: String,
      invert: Boolean = false
  ): Parameter[String] = {
    bounds match {
      case Some(sl) =>
        BaseParameter(
          key,
          s"ST_MakeEnvelope (${sl.left}, ${sl.bottom}, ${sl.right}, ${sl.top}, 4326)",
          Operator.AT,
          invert,
          true,
          Some(table)
        )
      case None => CustomParameter("")
    }
  }

  /**
    * Private method to help build a sub select on task_review and users.
    */
  private def buildReviewSubQuerySearch(
      params: SearchParameters,
      column: String,
      value: String,
      invertKey: String
  ): FilterGroup = {
    FilterGroup(
      List(
        SubQueryFilter(
          "id",
          Query.simple(
            List(
              BaseParameter("tr.task_id", "tasks.id", useValueDirectly = true),
              BaseParameter("u.name", s"'%${value}%'", Operator.ILIKE, useValueDirectly = true)
            ),
            s"SELECT task_id FROM task_review tr INNER JOIN users u ON u.id = tr.${column}"
          ),
          params.invertFields.getOrElse(List()).contains(invertKey),
          Operator.IN,
          Some("tasks")
        )
      )
    )
  }
}
