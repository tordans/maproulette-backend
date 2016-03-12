package org.maproulette.cache

import org.maproulette.models.BaseObject

import scala.collection.mutable.Map

/**
  * This is a very basic Cache Storage class that will store all items in memory. Ultimately this
  * class should be extended to use the Play Cache API
  *
  * @author cuthbertm
  */
class CacheStorage[Key, Value<:BaseObject[Key]] {
  protected val cache:Map[Key, Value] = Map.empty

  /**
    * Gets an object from the cache
    *
    * @param id The id of the object you are looking for
    * @return The object from the cache, None if not found
    */
  def get(id:Key) : Option[Value] = cache.get(id)

  /**
    * Finds an object from the cache based on the name instead of the id
    *
    * @param name The name of the object you wish to find
    * @return The object from the cache, None if not found
    */
  def find(name:String) : Option[Value] =
    cache.find(element => element._2.name.equalsIgnoreCase(name)) match {
      case Some(value) => Some(value._2)
      case None => None
    }

  /**
    * Adds an object to the cache
    *
    * @param obj The object to add to the cache
    * @return The object put in the cache, or None if it could not be placed in the cache
    */
  def add(obj:Value) : Option[Value] = cache.put(obj.id, obj)

  /**
    * Removes an object from the cache based on the id
    *
    * @param id the id of the object to be removed
    * @return The object removed from the cache, or None if it could not be removed from the cache,
    *         or was not originally in the cache
    */
  def remove(id:Key) : Option[Value] = cache.remove(id)

  /**
    * Remove an object from the cache based on the name
    *
    * @param name The name of the object to be removed
    * @return The object removed from the cache, or None if it could not be removed from the cache,
    *         or was not originally in the cache
    */
  def remove(name:String) : Option[Value] = this.find(name) match {
    case Some(value) => this.remove(value.id)
    case None => None
  }

  /**
    * Fully clears the cache, this may not be applicable for non basic in memory caches
    */
  def clear() = cache.clear()

  /**
    * The current size of the cache
    *
    * @return
    */
  def size : Int = cache.size

  /**
    * Checks if an item is cached or not
    *
    * @param id The id of the object to check to see if it is in the cache
    * @return true if the item is found in the cache
    */
  def isCached(id:Key) : Boolean = cache.contains(id)
}
