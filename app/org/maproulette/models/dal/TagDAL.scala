// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.models.dal

import java.sql.Connection
import javax.inject.{Inject, Provider, Singleton}

import anorm._
import anorm.SqlParser._
import org.maproulette.cache.{CacheManager, TagCacheManager}
import org.maproulette.exception.{InvalidException, UniqueViolationException}
import org.maproulette.models.Tag
import org.maproulette.permissions.Permission
import org.maproulette.session.User
import play.api.db.Database
import play.api.libs.json.JsValue

/**
  * This class manages all the data access layer for all the Tag objects in the system.
  *
  * @author cuthbertm
  */
@Singleton
class TagDAL @Inject() (override val db:Database,
                        tagCacheProvider: Provider[TagCacheManager],
                        override val permission:Permission) extends BaseDAL[Long, Tag] {
  // the name of the table in the database for tags
  override val tableName: String = "tags"

  override val cacheManager: CacheManager[Long, Tag] = tagCacheProvider.get()
  // the anorm row parser for the tag object
  val parser: RowParser[Tag] = {
    get[Long]("tags.id") ~
      get[String]("tags.name") ~
      get[Option[String]]("tags.description") map {
      case id ~ name ~ description =>
        new Tag(id, name.toLowerCase, description)
    }
  }

  /**
    * Inserts a new tag object into the database
    *
    * @param tag The tag object to insert into the database
    * @return The object that was inserted into the database. This will include the newly created id
    */
  override def insert(tag: Tag, user:User)(implicit c:Option[Connection]=None): Tag = {
    if (tag.name.isEmpty) {
      // do not allow empty tags
      throw new InvalidException(s"Tags cannot be empty strings")
    }
    this.permission.hasWriteAccess(tag, user)
    this.cacheManager.withOptionCaching { () =>
      this.withMRTransaction { implicit c =>
        SQL("INSERT INTO tags (name, description) VALUES ({name}, {description}) ON CONFLICT(LOWER(name)) DO NOTHING RETURNING *")
          .on('name -> tag.name.toLowerCase, 'description -> tag.description).as(this.parser.*).headOption
      }
    } match {
      case Some(t) => t
      case None => throw new UniqueViolationException(s"Tag with name ${tag.name} already exists in the database")
    }
  }

  /**
    * Updates a tag object in the databse
    *
    * @param tag A json object containing all the fields to update for the tag
    * @param id The id of the object that you are updating
    * @return An optional object, it will return None if no object found with a matching id that was supplied
    */
  override def update(tag:JsValue, user:User)(implicit id:Long, c:Option[Connection]=None): Option[Tag] = {

    this.cacheManager.withUpdatingCache(Long => retrieveById) { implicit cachedItem =>
      this.permission.hasWriteAccess(cachedItem, user)
      this.withMRTransaction { implicit c =>
        val name = (tag \ "name").asOpt[String].getOrElse(cachedItem.name)
        if (name.nonEmpty) {
          // do not allow empty tags
          throw new InvalidException(s"Tags cannot be empty strings")
        }
        val description = (tag \ "description").asOpt[String].getOrElse(cachedItem.description.getOrElse(""))
        val updatedTag = Tag(id, name, Some(description))

        SQL"""UPDATE tags SET name = ${updatedTag.name.toLowerCase}, description = ${updatedTag.description}
              WHERE id = $id RETURNING *""".as(this.parser.*).headOption
      }
    }
  }

  /**
    * We override the retrieveByName function so that we make sure that we test against a lower case version
    * of the name
    */
  override def retrieveByName(implicit name: String, parentId: Long, c: Option[Connection]): Option[Tag] = {
    super.retrieveByName(name.toLowerCase, parentId, c)
  }

  /**
    * Need to make sure all the names in the list are lowercase
    */
  override def retrieveListByName(implicit names: List[String], parentId: Long, c: Option[Connection]): List[Tag] = {
    super.retrieveListByName(names.map(_.toLowerCase), parentId, c)
  }

  /**
    * Need to make sure that the prefix is lowercase
    */
  override def retrieveListByPrefix(prefix: String, limit: Int, offset: Int, onlyEnabled: Boolean,
                                    orderColumn: String, orderDirection: String)
                                   (implicit parentId: Long, c: Option[Connection]): List[Tag] = {
    super.retrieveListByPrefix(prefix.toLowerCase, limit, offset, onlyEnabled, orderColumn, orderDirection)
  }

  /**
    * Make sure the searchString is lower case
    */
  override def find(searchString: String, limit: Int, offset: Int, onlyEnabled: Boolean,
                    orderColumn: String, orderDirection: String)
                   (implicit parentId: Long, c: Option[Connection]): List[Tag] = {
    super.find(searchString.toLowerCase, limit, offset, onlyEnabled, orderColumn, orderDirection)
  }

  /**
    * Get all the tags for a specific task
    *
    * @param id The id fo the task
    * @return List of tags for the task
    */
  def listByTask(id:Long)(implicit c:Option[Connection]=None) : List[Tag] = {
    implicit val ids:List[Long] = List()
    this.cacheManager.withIDListCaching { implicit uncached =>
      this.withMRConnection { implicit c =>
        SQL"""SELECT * FROM tags AS t
              INNER JOIN tags_on_tasks AS tt ON t.id = tt.tag_id
              WHERE tt.task_id = $id""".as(this.parser.*)
      }
    }
  }

  /**
    * Get all the tags for a specific challenge
    *
    * @param id The id of the challenge
    * @return List of tags for the challenge
    */
  def listByChallenge(id:Long)(implicit c:Option[Connection]=None) : List[Tag] = {
    implicit val ids:List[Long] = List()
    this.cacheManager.withIDListCaching { implicit uncached =>
      this.withMRConnection { implicit c =>
        SQL"""SELECT * FROM tags AS t
              INNER JOIN tags_on_challenges AS tc ON t.id = tc.tag_id
              WHERE tc.challenge_id = $id""".as(this.parser.*)
      }
    }
  }

  /**
    * This is an "upsert" function that will try and insert tags into the database based on a list,
    * it will either update the data for the tag if the tag already exists or create a new tag if
    * the tag does not exist. A tag is considered to exist if the id or the name is found in the
    * database/
    *
    * @param tags A list of tag objects to update/create in the database
    * @return Returns the list of tags that were inserted, this would include any newly created
    *         ids of tags.
    */
  def updateTagList(tags: List[Tag], user:User)(implicit c:Option[Connection]=None): List[Tag] = {
    if (tags.nonEmpty) {
      this.permission.hasSuperAccess(user)
      implicit val names = tags.filter(_.name.nonEmpty).map(_.name)
      this.cacheManager.withCacheNameDeletion { () =>
        this.withMRTransaction { implicit c =>
          val sqlQuery =
            s"""WITH upsert AS (UPDATE tags SET description = {description}
                                              WHERE id = {id} OR name = {name} RETURNING *)
                              INSERT INTO tags (name, description) SELECT {name}, {description}
                              WHERE NOT EXISTS (SELECT * FROM upsert)"""
          val parameters = tags.map(tag => {
            val descriptionString = tag.description match {
              case Some(d) => d
              case None => ""
            }
            Seq[NamedParameter]("name" -> tag.name.toLowerCase, "description" -> descriptionString, "id" -> tag.id)
          })
          BatchSql(sqlQuery, parameters.head, parameters.tail: _*).execute()
          this.retrieveListByName(names)
        }
      }
    } else {
      List.empty
    }
  }
}
