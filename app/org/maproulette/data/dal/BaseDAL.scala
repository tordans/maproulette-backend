package org.maproulette.data.dal

import java.sql.PreparedStatement

import anorm._
import anorm.SqlParser._
import org.maproulette.cache.CacheManager
import org.maproulette.data.BaseObject
import play.api.db.DB
import play.api.Play.current
import play.api.libs.json.JsValue

/**
  * @author cuthbertm
  */
trait BaseDAL[Key, T<:BaseObject[Key]] {
  val cacheManager:CacheManager[Key, T]
  val tableName:String
  implicit val caching:Boolean = true
  val parser:RowParser[T]
  // this allows for columns used in the retrieve functions to be optionally built
  val retrieveColumns:String = "*"

  /**
    * Our key for our objects are current Long, but can support String if need be
    *
    * @tparam Key
    * @return
    */
  def keyToStatement[Key] : ToStatement[Key] = {
    new ToStatement[Key] {
      def set(s: PreparedStatement, i: Int, identifier: Key) =
        identifier match {
          case id:String => ToStatement.stringToStatement.set(s, i, id)
          case Some(id:String) => ToStatement.stringToStatement.set(s, i, id)
          case id:Long => ToStatement.longToStatement.set(s, i, id)
          case Some(id:Long) => ToStatement.longToStatement.set(s, i, id)
          case intValue:Integer => ToStatement.integerToStatement.set(s, i, intValue)
        }
    }
  }

  def insert(element: T): T

  def update(tag:JsValue)(implicit id:Long): Option[T]

  /**
    * Deletes items of type T where the ID matches any of the ids in the given list
    *
    * @param ids List of ids for the objects we want to delete
    */
  def delete(implicit ids:List[Key]) : Unit = {
    cacheManager.withCacheIDDeletion { () =>
      DB.withTransaction { implicit c =>
        val query = s"DELETE FROM $tableName WHERE id IN ({ids})"
        val idSeq:Seq[Key] = ids.toSeq
        implicit val serializer = keyToStatement
        SQL(query).on('ids -> ParameterValue.toParameterValue(idSeq)(p = keyToStatement)).executeUpdate()
      }
    }
  }

  def delete(id: Key): Int = deleteFromIdList(List(id))

  def deleteFromIdList(implicit tags: List[Key]): Int = {
    cacheManager.withCacheIDDeletion { () =>
      DB.withTransaction { implicit c =>
        val query = s"DELETE FROM $tableName WHERE id IN ({tags})"
        SQL(query).on('tags -> ParameterValue.toParameterValue(tags)(p = keyToStatement)).executeUpdate()
      }
    }
  }

  def deleteFromStringList(implicit tags: List[String]): Int = {
    cacheManager.withCacheNameDeletion { () =>
      DB.withTransaction { implicit c =>
        val query = s"DELETE FROM $tableName WHERE name IN ({tags})"
        SQL(query).on('tags -> ParameterValue.toParameterValue(tags)).executeUpdate()
      }
    }
  }

  def retrieveById(implicit id:Key) : Option[T] = {
    cacheManager.withOptionCaching { () =>
      DB.withTransaction { implicit c =>
        val query = s"SELECT $retrieveColumns FROM $tableName WHERE id = {id}"
        SQL(query).on('id -> ParameterValue.toParameterValue(id)(p = keyToStatement)).as(parser *).headOption
      }
    }
  }

  def retrieveByName(implicit name:String) : Option[T] = {
    cacheManager.withOptionCaching { () =>
      DB.withTransaction { implicit c =>
        val query = s"SELECT $retrieveColumns FROM $tableName WHERE name = {name}"
        SQL(query).on('name -> name).as(parser *).headOption
      }
    }
  }

  def retrieveListById(limit: Int = (-1), offset: Int = 0)(implicit ids:List[Key]): List[T] = {
    cacheManager.withIDListCaching { implicit uncachedIDs =>
      DB.withTransaction { implicit c =>
        val limitValue = if (limit < 0) "ALL" else limit + ""
        val query = s"SELECT $retrieveColumns FROM $tableName " +
                    s"WHERE id IN ({inString}) LIMIT $limitValue OFFSET {offset}"
        SQL(query).on('inString -> ParameterValue.toParameterValue(uncachedIDs.toSeq)(p = keyToStatement), 'offset -> offset).as(parser *)
      }
    }
  }

  def retrieveListByName(implicit names: List[String]): List[T] = {
    cacheManager.withNameListCaching { implicit uncachedNames =>
      DB.withTransaction { implicit c =>
        val query = s"SELECT $retrieveColumns FROM $tableName WHERE name in ({inString})"
        SQL(query).on('inString -> ParameterValue.toParameterValue(names)).as(parser *)
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
  def retrieveListByPrefix(prefix: String, limit: Int = 10, offset: Int = 0): List[T] = {
    DB.withTransaction { implicit c =>
      val sqlPrefix = s"$prefix%"
      val sqlLimit = if (limit < 0) "ALL" else limit + ""
      val query = s"SELECT $retrieveColumns FROM $tableName " +
                  s"WHERE name LIKE {prefix} LIMIT $sqlLimit OFFSET {offset}"
      SQL(query).on('prefix -> sqlPrefix, 'offset -> offset).as(parser *)
    }
  }

  /**
    * This is a dangerous function as it will return all the objects available, so it could take up
    * a lot of memory
    */
  def list(limit:Int = 10, offset:Int = 0) : List[T] = {
    implicit val ids = List.empty
    cacheManager.withIDListCaching { implicit uncachedIDs =>
      DB.withTransaction { implicit c =>
        val sqlLimit = if (limit < 0) "ALL" else limit + ""
        val query = s"SELECT $retrieveColumns FROM $tableName LIMIT $sqlLimit OFFSET {offset}"
        SQL(query).on('offset -> ParameterValue.toParameterValue(offset)).as(parser *)
      }
    }
  }
}
