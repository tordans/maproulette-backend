package org.maproulette.data

import java.sql.Connection

import anorm.SqlParser._
import anorm._
import org.maproulette.cache.TagCacheManager
import play.api.Play.current
import play.api.db.DB
import play.api.libs.functional.syntax._
import play.api.libs.json._

/**
  * @author cuthbertm
  */
case class Tag(override val id: Long = (-1),
               override val name: String,
               description: Option[String] = None) extends BaseObject

object Tag {

  implicit val cacheManager = TagCacheManager
  val jsonReader: Reads[Tag] = (
    (JsPath \ "id").read[Long] and
      (JsPath \ "name").read[String] and
      (JsPath \ "description").readNullable[String]
    ) (Tag.apply _)
  val jsonWriter: Writes[Tag] = (
    (JsPath \ "id").write[Long] and
      (JsPath \ "name").write[String] and
      (JsPath \ "description").writeNullable[String]
    ) (unlift(Tag.unapply _))
  val parser: RowParser[Tag] = {
    get[Long]("tags.id") ~
      get[String]("tags.name") ~
      get[Option[String]]("tags.description") map {
      case id ~ name ~ description =>
        new Tag(id, name, description)
    }
  }

  def insert(tag: Tag): Tag = {
    cacheManager.withOptionCaching { () =>
      DB.withTransaction { implicit c: Connection =>
        val identifier =
          SQL"""INSERT INTO tags (name, description)
            VALUES (${tag.name}, ${tag.description}) RETURNING id""".as(long("id") *).head
        Some(tag.copy(id = identifier))
      }
    }.get
  }

  def update(id: Long, updates: JsValue): Option[Tag] = {
    implicit val retriveId = id
    cacheManager.withUpdatingCache(retrieveById) { implicit cached =>
      DB.withConnection { implicit c =>
        val nameString = (updates \ "name").asOpt[String] match {
          case Some(value) => value
          case None => cached.name
        }
        val descriptionString = (updates \ "description").asOpt[String] match {
          case Some(value) => value
          case None => cached.description match {
            case Some(desc) => desc
            case None => ""
          }
        }
        Some(SQL"""UPDATE tags SET name = $nameString, description = $descriptionString WHERE id = $id RETURNING *""".as(parser *).head)
      }
    }
  }

  def retrieveById(id: Long): Option[Tag] = {
    cacheManager.withOptionCaching { () =>
      DB.withConnection { implicit c =>
        SQL"""SELECT id, name, description FROM tags WHERE id = $id""".as(parser *).headOption
      }
    }
  }

  def retrieveByName(name: String): Option[Tag] = {
    cacheManager.withOptionCaching { () =>
      DB.withConnection { implicit c =>
        SQL"""SELECT id, name, description FROM tags WHERE name = $name""".as(parser *).headOption
      }
    }
  }

  def retrieveListById(ids: List[Long], limit: Int = (-1), offset: Int = 0): List[Tag] = {
    cacheManager.withIDListCaching { implicit uncachedIDs =>
      DB.withConnection { implicit c =>
        val limitValue = if (limit < 0) "ALL" else limit + ""
        val query = s"SELECT id, name, description FROM tags WHERE id IN ({inString}) LIMIT $limitValue OFFSET $offset"
        val inString = uncachedIDs.mkString(",")
        SQL(query).on('inString -> inString).as(parser *)
      }
    }
  }

  /**
    * This function will hit the database every time, so could be costly, and might be worthwhile
    * to use the cache. Only problem with using the cache is that it some what requires all the
    * tags to be available, and you don't necessarily want to load all the tags into memory. Although
    * maybe you do.
    *
    * @param prefix The prefix of the tag
    * @param limit  Limit the number of results to be returned
    * @return A list of tags that contain the supplied prefix
    */
  def retrieveListByPrefix(prefix: String, limit: Int = 10, offset: Int = 0): List[Tag] = {
    DB.withConnection { implicit c =>
      val sqlPrefix = s"$prefix%"
      val sqlLimit = if (limit < 0) "ALL" else limit + ""
      val query = s"SELECT id, name, description FROM tags WHERE name LIKE {prefix} LIMIT $sqlLimit OFFSET $offset"
      SQL(query).on('prefix -> sqlPrefix).as(parser *)
    }
  }

  def delete(id: Long): Int = deleteFromIdList(List(id))

  def deleteFromIdList(tags: List[Long]): Int = {
    implicit val idList = tags
    cacheManager.withCacheIDDeletion { () =>
      DB.withConnection { implicit c =>
        val inString = tags.mkString(",")
        SQL"""DELETE FROM tags WHERE id IN ($inString)""".executeUpdate()
      }
    }
  }

  def deleteFromStringList(tags: List[String]): Int = {
    implicit val names = tags
    cacheManager.withCacheNameDeletion { () =>
      DB.withConnection { implicit c =>
        val inString = tags.mkString("'", "','", "'")
        SQL"""DELETE FROM tags WHERE name IN ($inString)""".executeUpdate()
      }
    }
  }

  def updateTagList(tags: List[Tag]): List[Int] = {
    implicit val ids = tags.map(_.name)
    cacheManager.withCacheNameDeletion { () =>
      DB.withTransaction { implicit c =>
        val sqlQuery = "WITH upsert AS (UPDATE tags SET name = {name}, description = {description} " +
          "WHERE id = {id} OR name = {name} RETURNING *) " +
          "INSERT INTO tags (name, description) SELECT {name}, {description} " +
          "WHERE NOT EXISTS (SELECT * FROM upsert)"
        val parameters = tags.map(tag => {
          val descriptionString = tag.description match {
            case Some(d) => d
            case None => ""
          }
          Seq[NamedParameter]("name" -> tag.name, "description" -> descriptionString, "id" -> tag.id)
        })
        val batchUpsert = BatchSql(sqlQuery, parameters.head)
        //batchUpsert.addBatchParamsList()
        val result = batchUpsert.execute()
        retrieveListByName(tags.map(tag => tag.name))
        result.toList
      }
    }
  }

  def retrieveListByName(names: List[String]): List[Tag] = {
    implicit val identifiers = names
    cacheManager.withNameListCaching { implicit uncachedNames =>
      DB.withConnection { implicit c =>
        val inString = uncachedNames.mkString("'", "','", "'")
        SQL"""SELECT id, name, description FROM tags WHERE name IN ($inString)""".as(parser *)
      }
    }
  }

  //def updateTagListByNames(tags: List[String]): List[Int] = update(tags.map(new Tag(_)))
}
