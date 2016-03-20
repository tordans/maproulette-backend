package org.maproulette.models.dal

import java.sql.PreparedStatement

import anorm._
import anorm.SqlParser._
import org.maproulette.cache.CacheManager
import org.maproulette.models.BaseObject
import org.maproulette.session.User
import play.api.db.Database
import play.api.libs.json.JsValue

/**
  * Base Data access layer that handles all the deletes, retrievals and listing. Insert and Update
  * functions are required to be implemented by the classes that mix this trait in.
  *
  * @author cuthbertm
  */
trait BaseDAL[Key, T<:BaseObject[Key]] {
  // Manager to handle all the caching for this particular layer
  val cacheManager:CacheManager[Key, T]
  // The name of the table in the database
  val tableName:String
  // where caching is turned on or off by default.
  implicit val caching:Boolean = true
  // The object parser specific for this data access layer
  val parser:RowParser[T]
  // this allows for columns used in the retrieve functions to be optionally built
  val retrieveColumns:String = "*"
  // Database that should be injected in any implementing classes
  val db:Database

  /**
    * Our key for our objects are current Long, but can support String if need be. This function
    * handles transforming java objects to SQL for a specific set related to the object key
    *
    * @tparam Key The type of Key, this is currently always Long, but could be changed easily enough in the future
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
          case list:List[Long @unchecked] => ToStatement.listToStatement[Long].set(s, i, list)
        }
    }
  }

  /**
    * Insert function that must be implemented by the class that mixes in this trait
    *
    * @param element The element that you are inserting to the database
    * @param user The user executing the task
    * @return The object that was inserted into the database. This will include the newly created id
    */
  def insert(element: T, user:User): T

  /**
    * Update function that must be implemented by the class that mixes in this trait
    *
    * @param updates The updates in json form
    * @param user The user executing the task
    * @param id The id of the object that you are updating
    * @return An optional object, it will return None if no object found with a matching id that was supplied
    */
  def update(updates:JsValue, user:User)(implicit id:Long): Option[T]

  /**
    * Helper function that takes a single key to delete and pushes the workload off to the deleteFromIdList
    * function that takes a list. This just creates a list with a single element
    *
    * @param id The id that you want to delete
    * @param user The user executing the task
    * @return Count of deleted row(s)
    */
  def delete(id: Key, user:User): Int = deleteFromIdList(user)(List(id))

  /**
    * Deletes all the objects in the supplied id list. With caching, so after
    * it has deleted the objects from the database it will delete the same objects from the cache.
    *
    * @param user The user executing the task
    * @param ids The list of ids that will be deleted
    * @return Count of deleted row(s)
    */
  def deleteFromIdList(user:User)(implicit ids: List[Key]): Int = {
    // todo: add access checks here
    cacheManager.withCacheIDDeletion { () =>
      db.withTransaction { implicit c =>
        val query = s"DELETE FROM $tableName WHERE id IN ({ids})"
        SQL(query).on('ids -> ParameterValue.toParameterValue(ids)(p = keyToStatement)).executeUpdate()
      }
    }
  }

  /**
    * Deletes all the objects found matching names in the supplied list. With caching, so after
    * it has deleted the objects from the database it will delete the same objects from the cache.
    *
    * @param user The user executing the task
    * @param names The names to match and delete
    * @return Count of deleted row(s)
    */
  def deleteFromStringList(user:User)(implicit names: List[String]): Int = {
    // todo: add access checks here
    cacheManager.withCacheNameDeletion { () =>
      db.withTransaction { implicit c =>
        val query = s"DELETE FROM $tableName WHERE name IN ({names})"
        SQL(query).on('names -> ParameterValue.toParameterValue(names)).executeUpdate()
      }
    }
  }

  /**
    * A basic retrieval of the object based on the id. With caching, so if it finds
    * the object in the cache it will return that object without checking the database, otherwise
    * will hit the database directly.
    *
    * @param id The id of the object to be retrieved
    * @return The object, None if not found
    */
  def retrieveById(implicit id:Key) : Option[T] = {
    cacheManager.withOptionCaching { () =>
      db.withConnection { implicit c =>
        val query = s"SELECT $retrieveColumns FROM $tableName WHERE id = {id}"
        SQL(query).on('id -> ParameterValue.toParameterValue(id)(p = keyToStatement)).as(parser.singleOpt)
      }
    }
  }

  /**
    * Retrieves the object based on the name, this function is somewhat weak as there could be
    * multiple objects with the same name. The database only restricts the same name in combination
    * with a parent. So this will just return the first one it finds. With caching, so if it finds
    * the object in the cache it will return that object without checking the database, otherwise
    * will hit the database directly.
    *
    * TODO: fix this function, it will not work quite right. The parent id should probably be supplied
    * if you want to look it up by name.
    *
    * @param name The name you are looking up by
    * @return The object that you are looking up, None if not found
    */
  def retrieveByName(implicit name:String) : Option[T] = {
    cacheManager.withOptionCaching { () =>
      db.withConnection { implicit c =>
        val query = s"SELECT $retrieveColumns FROM $tableName WHERE name = {name}"
        SQL(query).on('name -> name).as(parser.singleOpt)
      }
    }
  }

  /**
    * Retrieves an object based on the identifier. With caching, so if it finds the object in the
    * cache it will return that object without checking the database, otherwise will hit the
    * database directly.
    *
    * TODO: This, and the concept of an identifier, should probably be removed. The name of the
    * object can handle this better, plus the database doesn't force the value to be unique so
    * it would cause issues.
    *
    * @param identifer
    * @return
    */
  def retrieveByIdentifier(implicit identifer:String) : Option[T] = {
    cacheManager.withOptionCaching { () =>
      db.withConnection { implicit c =>
        val query = s"SELECT $retrieveColumns FROM $tableName WHERE identifier = {identifier}"
        SQL(query).on('identifier -> identifer).as(parser.singleOpt)
      }
    }
  }

  /**
    * Retrieves a list of objects from the supplied list of ids. Will check for any objects currently
    * in the cache and those that aren't will be retrieved from the database
    *
    * @param limit The limit on the number of objects returned. This is not entirely useful as a limit
    *              could be set simply by how many ids you supplied in the list, but possibly useful
    *              for paging
    * @param offset For paging, ie. the page number starting at 0
    * @param ids The list of ids to be retrieved
    * @return A list of objects, empty list if none found
    */
  def retrieveListById(limit: Int = (-1), offset: Int = 0)(implicit ids:List[Key]): List[T] = {
    cacheManager.withIDListCaching { implicit uncachedIDs =>
      db.withConnection { implicit c =>
        val limitValue = if (limit < 0) "ALL" else limit + ""
        val query = s"SELECT $retrieveColumns FROM $tableName " +
                    s"WHERE id IN ({inString}) LIMIT $limitValue OFFSET {offset}"
        SQL(query).on('inString -> ParameterValue.toParameterValue(uncachedIDs.toSeq)(p = keyToStatement), 'offset -> offset).as(parser.*)
      }
    }
  }

  /**
    * Same as retrieveListById except no paging built in. Will check for any objects currently in the
    * cache and those that aren't will be retrieved from the database.
    *
    * @param names The names to be retrieved
    * @return List of objects, empty list if none found
    */
  def retrieveListByName(implicit names: List[String]): List[T] = {
    cacheManager.withNameListCaching { implicit uncachedNames =>
      db.withConnection { implicit c =>
        val query = s"SELECT $retrieveColumns FROM $tableName WHERE name in ({inString})"
        SQL(query).on('inString -> ParameterValue.toParameterValue(names)).as(parser.*)
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
    db.withConnection { implicit c =>
      val sqlPrefix = s"$prefix%"
      val sqlLimit = if (limit < 0) "ALL" else limit + ""
      val query = s"SELECT $retrieveColumns FROM $tableName " +
                  s"WHERE name LIKE {prefix} LIMIT $sqlLimit OFFSET {offset}"
      SQL(query).on('prefix -> sqlPrefix, 'offset -> offset).as(parser.*)
    }
  }

  /**
    * This is a dangerous function as it will return all the objects available, so it could take up
    * a lot of memory
    */
  def list(limit:Int = 10, offset:Int = 0) : List[T] = {
    implicit val ids = List.empty
    cacheManager.withIDListCaching { implicit uncachedIDs =>
      db.withConnection { implicit c =>
        val sqlLimit = if (limit < 0) "ALL" else limit + ""
        val query = s"SELECT $retrieveColumns FROM $tableName LIMIT $sqlLimit OFFSET {offset}"
        SQL(query).on('offset -> ParameterValue.toParameterValue(offset)).as(parser.*)
      }
    }
  }
}
