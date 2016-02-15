package org.maproulette.data.dal

import anorm._
import anorm.SqlParser._
import org.maproulette.cache.CacheManager
import org.maproulette.data.BaseObject
import play.api.db.DB
import play.api.Play.current

/**
  * @author cuthbertm
  */
trait BaseDAL[T<:BaseObject] {
  implicit val cacheManager:CacheManager[T]
  implicit val tableName:String
  implicit val caching:Boolean = true
  implicit val parser:RowParser[T]
  // this allows for columns used in the retrieve functions to be optionally built
  implicit val retrieveColumns:String = "*"

  /**
    * Deletes items of type T where the ID matches any of the ids in the given list
    *
    * @param ids List of ids for the objects we want to delete
    */
  def delete(implicit ids:List[Long]) : Unit = {
    cacheManager.withCacheIDDeletion { () =>
      DB.withConnection { implicit c =>
        val query = s"DELETE FROM $tableName WHERE id IN ({ids})"
        SQL(query).on('ids -> ids).executeUpdate()
      }
    }
  }

  def delete(id: Long): Int = deleteFromIdList(List(id))

  def deleteFromIdList(implicit tags: List[Long]): Int = {
    cacheManager.withCacheIDDeletion { () =>
      DB.withConnection { implicit c =>
        val query = s"DELETE FROM $tableName WHERE id IN ({tags})"
        SQL(query).on('tags -> tags).executeUpdate()
      }
    }
  }

  def deleteFromStringList(implicit tags: List[String]): Int = {
    cacheManager.withCacheNameDeletion { () =>
      DB.withConnection { implicit c =>
        val query = s"DELETE FROM $tableName WHERE name IN ({tags})"
        SQL(query).on('tags -> tags).executeUpdate()
      }
    }
  }

  def retrieveById(implicit id:Long) : Option[T] = {
    cacheManager.withOptionCaching { () =>
      DB.withConnection { implicit c =>
        val query = s"SELECT $retrieveColumns FROM $tableName WHERE id = {id}"
        SQL(query).on('id -> id).as(parser *).headOption
      }
    }
  }

  def retrieveByName(implicit name:String) : Option[T] = {
    cacheManager.withOptionCaching { () =>
      DB.withConnection { implicit c =>
        val query = s"SELECT $retrieveColumns FROM $tableName WHERE name = {name}"
        SQL(query).on('name -> name).as(parser *).headOption
      }
    }
  }

  def retrieveListById(limit: Int = (-1), offset: Int = 0)(implicit ids:List[Long]): List[T] = {
    cacheManager.withIDListCaching { implicit uncachedIDs =>
      DB.withConnection { implicit c =>
        val limitValue = if (limit < 0) "ALL" else limit + ""
        val query = s"SELECT $retrieveColumns FROM $tableName WHERE id IN ({inString}) LIMIT $limitValue OFFSET $offset"
        val inString = uncachedIDs.mkString(",")
        SQL(query).on('inString -> inString).as(parser *)
      }
    }
  }

  def retrieveListByName(implicit names: List[String]): List[T] = {
    cacheManager.withNameListCaching { implicit uncachedNames =>
      DB.withConnection { implicit c =>
        val inString = uncachedNames.mkString("'", "','", "'")
        SQL"""SELECT $retrieveColumns FROM $tableName WHERE name IN ($inString)""".as(parser *)
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
    DB.withConnection { implicit c =>
      val sqlPrefix = s"$prefix%"
      val sqlLimit = if (limit < 0) "ALL" else limit + ""
      val query = s"SELECT $retrieveColumns FROM $tableName WHERE name LIKE {prefix} LIMIT $sqlLimit OFFSET $offset"
      SQL(query).on('prefix -> sqlPrefix).as(parser *)
    }
  }
}
