package org.maproulette.data.dal

import anorm._
import anorm.SqlParser._
import org.maproulette.cache.TagCacheManager
import org.maproulette.data.Tag
import org.maproulette.utils.Utils
import play.api.db.DB
import play.api.Play.current
import play.api.libs.json.JsValue

/**
  * @author cuthbertm
  */
object TagDAL extends BaseDAL[Long, Tag] {
  override val cacheManager = TagCacheManager
  override val tableName: String = "tags"
  val parser: RowParser[Tag] = {
    get[Long]("tags.id") ~
      get[String]("tags.name") ~
      get[Option[String]]("tags.description") map {
      case id ~ name ~ description =>
        new Tag(id, name.toLowerCase, description)
    }
  }

  override def insert(tag: Tag): Tag = {
    cacheManager.withOptionCaching { () =>
      DB.withTransaction { implicit c =>
        SQL("INSERT INTO tags (name, description) VALUES ({name}, {description}) RETURNING *")
          .on('name -> tag.name.toLowerCase, 'description -> tag.description).as(parser *).headOption
      }
    }.get
  }

  override def update(tag:JsValue)(implicit id:Long): Option[Tag] = {
    cacheManager.withUpdatingCache(Long => retrieveById) { implicit cachedItem =>
      DB.withConnection { implicit c =>
        val name = Utils.getDefaultOption((tag \ "name").asOpt[String], cachedItem.name)
        val description = Utils.getDefaultOption(
          (tag \ "description").asOpt[String],
          Utils.getDefaultOption(cachedItem.description, "")
        )
        val updatedTag = Tag(id, name, Some(description))

        SQL"""UPDATE tags SET name = ${updatedTag.name.toLowerCase}, description = ${updatedTag.description}
              WHERE id = $id RETURNING *""".as(parser *).headOption
      }
    }
  }

  def updateTagList(tags: List[Tag]): List[Tag] = {
    implicit val names = tags.map(_.name)
    cacheManager.withCacheNameDeletion { () =>
      DB.withTransaction { implicit c =>
        val sqlQuery = s"WITH upsert AS (UPDATE tags SET name = {name}, description = {description} " +
          "WHERE id = {id} OR name = {name} RETURNING *) " +
          s"INSERT INTO tags (name, description) SELECT {name}, {description} " +
          "WHERE NOT EXISTS (SELECT * FROM upsert)"
        val parameters = tags.map(tag => {
          val descriptionString = tag.description match {
            case Some(d) => d
            case None => ""
          }
          Seq[NamedParameter]("name" -> tag.name, "description" -> descriptionString, "id" -> tag.id)
        })
        val batchUpsert = BatchSql(sqlQuery, parameters.head, parameters.tail:_*)
        val result = batchUpsert.execute()
        retrieveListByName(names)
      }
    }
  }
}
