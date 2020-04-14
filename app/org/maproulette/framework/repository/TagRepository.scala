/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.repository

import java.sql.Connection

import anorm.SqlParser.get
import anorm.{BatchSql, NamedParameter, RowParser, SQL, ~}
import javax.inject.Inject
import org.maproulette.exception.UniqueViolationException
import org.maproulette.framework.model.Tag
import org.maproulette.framework.psql.filter.{BaseParameter, Filter, FilterGroup, Operator}
import org.maproulette.framework.psql.{OR, Query}
import play.api.db.Database

/**
  * @author mcuthbert
  */
class TagRepository @Inject() (override val db: Database) extends RepositoryMixin {

  /**
    * Retrieves a specific tag
    *
    * @param id The id for the tag
    * @param c  Implicit provided optional connection
    * @return An optional tag
    */
  def retrieve(id: Long)(implicit c: Option[Connection] = None): Option[Tag] = {
    this.query(Query.simple(List(BaseParameter(Tag.FIELD_ID, id)))).headOption
  }

  /**
    * Create a new Tag object
    *
    * @param tag The tag to create
    * @param c   An implicit connection
    * @return The newly created Tag
    */
  def create(tag: Tag)(implicit c: Option[Connection] = None): Tag = {
    this.withMRTransaction { implicit c =>
      SQL(
        """INSERT INTO tags (name, description, tag_type)
          |VALUES ({name}, {description}, {tagType})
          |ON CONFLICT(LOWER(name), tag_type) DO NOTHING RETURNING *""".stripMargin
      ).on(
          Symbol("name")        -> tag.name.toLowerCase,
          Symbol("description") -> tag.description,
          Symbol("tagType")     -> tag.tagType
        )
        .as(TagRepository.parser.*)
        .headOption match {
        case Some(t) => t
        case None =>
          throw new UniqueViolationException(
            s"Tag with name ${tag.name} already exists in the database"
          )
      }
    }
  }

  /**
    * Updates a tag, only the name and description fields can be updated
    *
    * @param tag The tag to update
    * @param c   an implicit connection
    * @return The updated Tag object
    */
  def update(tag: Tag)(implicit c: Option[Connection] = None): Option[Tag] = {
    this.withMRTransaction { implicit c =>
      SQL("UPDATE tags SET name = {name}, description = {description} WHERE id = {id} RETURNING *")
        .on(
          Symbol("name")        -> tag.name.toLowerCase,
          Symbol("description") -> tag.description,
          Symbol("id")          -> tag.id
        )
        .as(TagRepository.parser.*)
        .headOption
    }
  }

  def delete(id: Long)(implicit c: Option[Connection] = None): Boolean = {
    this.withMRTransaction { implicit c =>
      SQL("DELETE FROM tags WHERE id = {id}").on(Symbol("id") -> id).execute()
    }
  }

  def listByChallenges(id: List[Long]): Map[Long, List[Tag]] = {
    this.withMRConnection { implicit c =>
      val challengeTagParser: RowParser[(Long, Tag)] = {
        TagRepository.parser ~
          get[Long]("tags_on_challenges.challenge_id") map {
          case tag ~ challengeId => (challengeId, tag)
        }
      }

      Query
        .simple(
          List(BaseParameter(s"tc.${Tag.FIELD_CHALLENGE_ID}", id, Operator.IN)),
          "SELECT * FROM tags AS t INNER JOIN tags_on_challenges AS tc ON t.id = tc.tag_id"
        )
        .build()
        .as(challengeTagParser.*)
        .groupBy(_._1)
        .map {
          case (k, v) => (k, v.map(_._2))
        }
    }
  }

  def updateTagList(tags: List[Tag])(implicit c: Option[Connection] = None): List[Tag] = {
    if (tags.nonEmpty) {
      this.withMRTransaction { implicit c =>
        val sqlQuery =
          s"""WITH upsert AS (UPDATE tags SET description = {description}
                              WHERE id = {id} OR (name = {name} AND tag_type = {tagType}) RETURNING *)
                              INSERT INTO tags (name, description, tag_type) SELECT {name}, {description}, {tagType}
                              WHERE NOT EXISTS (SELECT * FROM upsert)"""
        val parameters = tags.map(tag => {
          val descriptionString = tag.description match {
            case Some(d) => d
            case None    => ""
          }
          Seq[NamedParameter](
            "name"        -> tag.name.toLowerCase,
            "description" -> descriptionString,
            "id"          -> tag.id,
            "tagType"     -> tag.tagType
          )
        })
        BatchSql(sqlQuery, parameters.head, parameters.tail: _*).execute()
      }

      val tagFilterGroups = tags.map(tag =>
        FilterGroup(
          List(
            BaseParameter(Tag.FIELD_NAME, tag.name.toLowerCase.replaceAll("'", "")),
            BaseParameter(Tag.FIELD_TAG_TYPE, tag.tagType)
          )
        )
      )
      this.query(Query(Filter(tagFilterGroups, OR())))
    } else {
      List.empty
    }
  }

  /**
    * Query function that allows a user to build their own query against the Tags table
    *
    * @param query The query to execute
    * @param c     An implicit connection
    * @return A list of returned Comments
    */
  def query(query: Query)(implicit c: Option[Connection] = None): List[Tag] = {
    this.withMRConnection { implicit c =>
      query.build(s"SELECT * FROM tags").as(TagRepository.parser.*)
    }
  }
}

object TagRepository {
  val parser: RowParser[Tag] = {
    get[Long]("tags.id") ~
      get[String]("tags.name") ~
      get[Option[String]]("tags.description") ~
      get[String]("tags.tag_type") map {
      case id ~ name ~ description ~ tagType =>
        new Tag(id, name.toLowerCase, description, tagType = tagType)
    }
  }
}
