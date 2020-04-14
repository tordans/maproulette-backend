/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.repository

import java.sql.Connection

import anorm.SqlParser.get
import anorm.{RowParser, ~}
import javax.inject.{Inject, Singleton}
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import org.maproulette.framework.model._
import org.maproulette.framework.psql.{Paging, Query}
import play.api.db.Database

/**
  * The challenge repository handles all the querying with the databases related to
  * challenge list objects
  *
  * @author krotstan
  */
@Singleton
class ChallengeListingRepository @Inject() (override val db: Database) extends RepositoryMixin {

  /**
    * Query function that allows a user to build their own query against the Challenge table
    *
    * @param query The query to execute
    * @param c An implicit connection
    * @return A list of returned Challenges
    */
  def query(query: Query)(implicit c: Option[Connection] = None): List[ChallengeListing] = {
    withMRConnection { implicit c =>
      query
        .build(s"""SELECT ${ChallengeListingRepository.standardColumns} FROM challenges c
          INNER JOIN projects p ON p.id = c.parent_id
          INNER JOIN tasks t ON t.parent_id = c.id
          LEFT OUTER JOIN task_review ON task_review.task_id = t.id
          LEFT OUTER JOIN virtual_project_challenges vp ON c.id = vp.challenge_id
          """)
        .as(ChallengeListingRepository.parser.*)
    }
  }
}

object ChallengeListingRepository {
  val standardColumns: String =
    "c.id, c.parent_id, c.name, c.enabled, array_remove(array_agg(vp.project_id), NULL) AS virtual_parent_ids"

  /**
    * The row parser for Anorm to enable the object to be read from the retrieved row directly
    * to the ChallengeListing object.
    */
  val parser: RowParser[ChallengeListing] = {
    get[Long]("challenges.id") ~
      get[Long]("challenges.parent_id") ~
      get[String]("challenges.name") ~
      get[Boolean]("challenges.enabled") ~
      get[Option[Array[Long]]]("virtual_parent_ids") map {
      case id ~ parent ~ name ~ enabled ~ virtualParents =>
        ChallengeListing(id, parent, name, enabled, virtualParents)
    }
  }
}
