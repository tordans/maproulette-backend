package org.maproulette.models.dal

import java.sql.{Connection, PreparedStatement}

import anorm._
import anorm.SqlParser._
import org.joda.time.DateTime
import org.maproulette.actions.ItemType
import org.maproulette.cache.CacheManager
import org.maproulette.exception.LockedException
import org.maproulette.models.{BaseObject, Lock}
import org.maproulette.models.utils.{DALHelper, TransactionManager}
import org.maproulette.permissions.Permission
import org.maproulette.session.User
import play.api.db.Database
import play.api.libs.json.JsValue

/**
  * Base Data access layer that handles all the deletes, retrievals and listing. Insert and Update
  * functions are required to be implemented by the classes that mix this trait in.
  *
  * @author cuthbertm
  */
trait BaseDAL[Key, T<:BaseObject[Key]] extends DALHelper with TransactionManager {
  // Service that handles all the permissions for the objects
  val permission:Permission
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
  // extra filters that will be applied to all queries. It is very important to not allow any user
  // inputed string values in this field, as this could lead to sql injection
  val extraFilters:String = ""

  def clearCaches = cacheManager.clearCaches

  implicit val lockedParser:RowParser[Lock] = {
    get[Option[DateTime]]("locked.locked_time") ~
    get[Option[Int]]("locked.item_type") ~
    get[Option[Long]]("locked.item_id") ~
    get[Option[Long]]("locked.user_id") map {
      case locked_time ~ itemType ~ itemId ~ userId =>
        locked_time match {
          case Some(d) => Lock(locked_time.get, itemType.get, itemId.get, userId.get)
          case None => Lock.emptyLock
        }
    }
  }

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

  def getDatabase = db

  /**
    * Insert function that must be implemented by the class that mixes in this trait
    *
    * @param element The element that you are inserting to the database
    * @param user The user executing the task
    * @return The object that was inserted into the database. This will include the newly created id
    */
  def insert(element: T, user:User)(implicit c:Connection=null): T

  /**
    * Update function that must be implemented by the class that mixes in this trait
    *
    * @param updates The updates in json form
    * @param user The user executing the task
    * @param id The id of the object that you are updating
    * @return An optional object, it will return None if no object found with a matching id that was supplied
    */
  def update(updates:JsValue, user:User)(implicit id:Key, c:Connection=null): Option[T]

  /**
    * This is a merge update function that will update the function if it exists otherwise it will
    * insert a new item. By default unless the implementing class overrides this function, the
    * mergeUpdate will simply attempt an insert
    *
    * @param element The element that needs to be inserted or updated. Although it could be updated,
    *                it requires the element itself in case it needs to be inserted
    * @param user The user that is executing the function
    * @param id The id of the element that is being updated/inserted
    * @param c A connection to execute against
    * @return
    */
  def mergeUpdate(element: T, user:User)(implicit id:Key, c:Connection=null) : Option[T] =
    Some(insert(element, user))

  /**
    * Update function that must be implemented by the class that mixes in this trait. This update
    * function updates based on the name instead of the id. This is the default update function,
    * the downside of this function is that it will first retrieve the item from cache and then
    * attempt to update it. For a more efficient method, the implementing class would need to
    * override this and then execute the update function filtering on the name and parentId.
    *
    * @param updates The updates in json form
    * @param user The user executing the update
    * @param name The name of the object that you are updating
    * @return An optional object, it will return None if no object found with a matching id that was supplied
    */
  def updateByName(updates:JsValue, user:User)(implicit name:String, parentId:Long=(-1), c:Connection=null): Option[T] =
    cacheManager.updateNameCache(String => retrieveByName) match {
      case Some(objID) => update(updates, user)(objID)
      case None => None
    }

  /**
    * Helper function that takes a single key to delete and pushes the workload off to the deleteFromIdList
    * function that takes a list. This just creates a list with a single element
    *
    * @param id The id that you want to delete
    * @param user The user executing the task
    * @return Count of deleted row(s)
    */
  def delete(id: Key, user:User)(implicit c:Connection=null): Int = deleteFromIdList(user)(List(id))

  /**
    * Deletes all the objects in the supplied id list. With caching, so after
    * it has deleted the objects from the database it will delete the same objects from the cache.
    *
    * @param user The user executing the task
    * @param ids The list of ids that will be deleted
    * @return Count of deleted row(s)
    */
  def deleteFromIdList(user:User)(implicit ids: List[Key], c:Connection=null): Int = {
    // todo: add access checks here
    cacheManager.withCacheIDDeletion { () =>
      withMRTransaction { implicit c =>
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
  def deleteFromStringList(user:User)(implicit names: List[String], parentId:Long=(-1), c:Connection=null): Int = {
    // todo: add access checks here
    cacheManager.withCacheNameDeletion { () =>
      withMRTransaction { implicit c =>
        val query = s"DELETE FROM $tableName WHERE name IN ({names}) ${parentFilter(parentId)}"
        SQL(query).on('names -> ParameterValue.toParameterValue(names)).executeUpdate()
      }
    }
  }

  /**
    * This will retrieve the root object in the hierarchy of the object, by default the root
    * object is itself.
    *
    * @param obj This is either the id of the object, or the object itself
    * @param user
    * @param c The connection if any
    * @return The object that it is retrieving
    */
  def retrieveRootObject(obj:Either[Key, T], user:User)(implicit c:Connection=null) : Option[_<:BaseObject[Key]] = {
    obj match {
      case Left(id) => retrieveById(id, c)
      case Right(obj) => Some(obj)
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
  def retrieveById(implicit id:Key, c:Connection=null) : Option[T] = {
    cacheManager.withCaching { () =>
      withMRConnection { implicit c =>
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
    * @param name The name you are looking up by
    * @return The object that you are looking up, None if not found
    */
  def retrieveByName(implicit name:String, parentId:Long=(-1), c:Connection=null) : Option[T] = {
    cacheManager.withOptionCaching { () =>
      withMRConnection { implicit c =>
        val query = s"SELECT $retrieveColumns FROM $tableName WHERE name = {name} ${parentFilter(parentId)}"
        SQL(query).on('name -> name).as(parser.singleOpt)
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
  def retrieveListById(limit: Int = (-1), offset: Int = 0)(implicit ids:List[Key], c:Connection=null): List[T] = {
    if (ids.isEmpty) {
      List.empty
    } else {
      cacheManager.withIDListCaching { implicit uncachedIDs =>
        withMRConnection { implicit c =>
          val query =
            s"""SELECT $retrieveColumns FROM $tableName
                          WHERE id IN ({inString})
                          LIMIT ${sqlLimit(limit)} OFFSET {offset}"""
          SQL(query).on('inString -> ParameterValue.toParameterValue(uncachedIDs)(p = keyToStatement),
            'offset -> offset).as(parser.*)
        }
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
  def retrieveListByName(implicit names: List[String], parentId:Long=(-1), c:Connection=null): List[T] = {
    if (names.isEmpty) {
      List.empty
    } else {
      cacheManager.withNameListCaching { implicit uncachedNames =>
        withMRConnection { implicit c =>
          val query = s"SELECT $retrieveColumns FROM $tableName WHERE name in ({inString}) ${parentFilter(parentId)}"
          SQL(query).on('inString -> ParameterValue.toParameterValue(uncachedNames)).as(parser.*)
        }
      }
    }
  }

  /**
    * This function will hit the database every time, so could be costly, and might be worthwhile
    * to use the cache. Only problem with using the cache is that it some what requires all the
    * tags to be available, and you don't necessarily want to load all the tags into memory. Although
    * maybe you do.
    *
    * @param prefix The prefix of the "name" field in the database
    * @param limit  Limit the number of results to be returned
    * @return A list of tags that contain the supplied prefix
    */
  def retrieveListByPrefix(prefix: String, limit: Int = 10, offset: Int = 0, onlyEnabled:Boolean=false,
                           orderColumn:String="id", orderDirection:String="ASC")
                          (implicit parentId:Long=(-1), c:Connection=null): List[T] =
    _find(s"$prefix%", limit, offset, onlyEnabled, orderColumn, orderDirection)

  /**
    * Same database concerns as retrieveListByPrefix. This find function will search the "name"
    * field for any references of the search string. So will be wrapped by %%, eg. LIKE %test%
    *
    * @param searchString The string to search for within the name field
    * @param limit Limit the number of results to be returned
    * @param offset For paging, ie. the page number starting at 0
    * @return A list of tags that contain the supplied prefix
    */
  def find(searchString:String, limit:Int = 10, offset:Int = 0, onlyEnabled:Boolean=false,
           orderColumn:String="id", orderDirection:String="ASC")
          (implicit parentId:Long=(-1), c:Connection=null) : List[T] =
    _find(s"%$searchString%", limit, offset, onlyEnabled, orderColumn, orderDirection)

  def _find(searchString:String, limit:Int = 10, offset:Int = 0, onlyEnabled:Boolean=false,
            orderColumn:String="id", orderDirection:String="ASC")
           (implicit parentId:Long=(-1), c:Connection=null) : List[T] = {
    withMRConnection { implicit c =>
      val query = s"""SELECT $retrieveColumns FROM $tableName
                      WHERE ${searchField("name", "")} ${enabled(onlyEnabled)} ${addExtraFilters(extraFilters)}
                      ${parentFilter(parentId)}
                      ${order(Some(orderColumn), orderDirection)}
                      LIMIT ${sqlLimit(limit)} OFFSET {offset}"""
      SQL(query).on('ss -> searchString, 'offset -> offset).as(parser.*)
    }
  }

  /**
    * This is a dangerous function as it will return all the objects available, so it could take up
    * a lot of memory
    */
  def list(limit:Int = 10, offset:Int = 0, onlyEnabled:Boolean=false, searchString:String="",
           orderColumn:String="id", orderDirection:String="ASC")
          (implicit parentId:Long=(-1), c:Connection=null) : List[T] = {
    implicit val ids = List.empty
    cacheManager.withIDListCaching { implicit uncachedIDs =>
      withMRConnection { implicit c =>
        val query = s"""SELECT $retrieveColumns FROM $tableName
                        WHERE ${searchField("name", "")}
                        ${enabled(onlyEnabled)} ${addExtraFilters(extraFilters)} ${parentFilter(parentId)}
                        ${order(Some(orderColumn), orderDirection)}
                        LIMIT ${sqlLimit(limit)} OFFSET {offset}"""
        SQL(query).on('ss -> search(searchString),
          'offset -> ParameterValue.toParameterValue(offset)
        ).as(parser.*)
      }
    }
  }

  /**
    * Locks an item in the database.
    *
    * @param user The user requesting the lock
    * @param item The item wanting to be locked
    * @param c A sql connection that is implicitly passed in from the calling function, this is an
    *          implicit function because this will always be called from within the code and never
    *          directly from an API call
    * @return true if successful
    */
  def lockItem(user:User, item:T)(implicit c:Connection=null) : Int =
    withMRTransaction { implicit c =>
      // first check to see if the item is already locked
      val checkQuery =
        s"""SELECT user_id FROM locked WHERE item_id = {itemId} AND item_type = ${item.itemType.typeId} FOR UPDATE"""
      SQL(checkQuery).on('itemId -> ParameterValue.toParameterValue(item.id)(p = keyToStatement)).as(SqlParser.long("user_id").singleOpt) match {
        case Some(id) =>
          if (id == user.id) {
            val query = s"UPDATE locked SET date = NOW() WHERE user_id = ${user.id} AND item_id = {itemId} AND item_type = ${item.itemType.typeId}"
            SQL(query).on('itemId -> ParameterValue.toParameterValue(item.id)(p = keyToStatement)).executeUpdate()
          } else {
            0
            //throw new LockedException(s"Could not acquire lock on object [${item.id}, already locked by user [$id]")
          }
        case None =>
          val query = s"INSERT INTO locked (item_type, item_id, user_id) VALUES (${item.itemType.typeId}, {itemId}, ${user.id})"
          SQL(query).on('itemId -> ParameterValue.toParameterValue(item.id)(p = keyToStatement)).executeUpdate()
      }
    }

  /**
    * Unlocks an item in the database
    *
    * @param user The user requesting to unlock the item
    * @param item The item being unlocked
    * @param c A sql connection that is implicitly passed in from the calling function, this is an
    *          implicit function because this will always be called from within the code and never
    *          directly from an API call
    * @return true if successful
    */
  def unlockItem(user:User, item:T)(implicit c:Connection=null) : Int =
    withMRTransaction { implicit c =>
      val checkQuery = s"""SELECT user_id FROM locked WHERE item_id = {itemId} AND item_type = ${item.itemType.typeId} FOR UPDATE"""
      SQL(checkQuery).on('itemId -> ParameterValue.toParameterValue(item.id)(p = keyToStatement)).as(SqlParser.long("user_id").singleOpt) match {
        case Some(id) =>
          if (id == user.id) {
            val query = s"""DELETE FROM locked WHERE user_id = ${user.id} AND item_id = {itemId} AND item_type = ${item.itemType.typeId}"""
            SQL(query).on('itemId -> ParameterValue.toParameterValue(item.id)(p = keyToStatement)).executeUpdate()
          } else {
            throw new LockedException(s"Item [${item.id}] currently locked by different user. [${user.id}")
          }
        case None => throw new LockedException(s"Item [${item.id}] trying to unlock does not exist.")
      }
    }

  /**
    * Unlocks all the items that are associated with the current user
    *
    * @param user The user
    * @param c an implicit connection, this function should generally be executed in conjunction
    *          with other requests
    * @return Number of locks removed
    */
  def unlockAllItems(user:User, itemType:Option[ItemType]=None)(implicit c:Connection=null) : Int =
    withMRTransaction { implicit c =>
      itemType match {
        case Some(it) =>
          SQL"""DELETE FROM locked WHERE user_id = ${user.id} AND item_type = ${it.typeId}""".executeUpdate()
        case None =>
          SQL"""DELETE FROM locked WHERE user_id = ${user.id}""".executeUpdate()
      }
    }
}
