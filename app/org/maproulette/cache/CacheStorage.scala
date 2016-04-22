package org.maproulette.cache

import org.joda.time.{LocalDateTime, Seconds}
import org.maproulette.models.BaseObject

import scala.collection.mutable.Map

case class InnerValue[Key, Value<:BaseObject[Key]](value:Value, accessTime:LocalDateTime)

/**
  * This is a very basic Cache Storage class that will store all items in memory. Ultimately this
  * class should be extended to use the Play Cache API
  *
  * cacheLimit - The number of entries allowed in the cache until it starts kicking out the oldest entries
  * cacheExpiry - The number of seconds allowed until a cache item is expired out the cache lazily
  *
  * @author cuthbertm
  */
class CacheStorage[Key, Value<:BaseObject[Key]] (cacheLimit:Int=10000, cacheExpiry:Int=900) {
  protected val cache:Map[Key, InnerValue[Key, Value]] = Map.empty

  /**
    * Gets an object from the cache
    *
    * @param id The id of the object you are looking for
    * @return The object from the cache, None if not found
    */
  def get(id:Key) : Option[Value] = synchronized {
    cache.get(id) match {
      case Some(value) =>
        if (isExpired(value)) {
          None
        } else {
          // because it has been touched, we need to update the accesstime
          add(value.value)
          Some(value.value)
        }
      case None => None
    }
  }

  /**
    * Finds an object from the cache based on the name instead of the id
    *
    * @param name The name of the object you wish to find
    * @return The object from the cache, None if not found
    */
  def find(name:String) : Option[Value] = synchronized {
    cache.find(element => element._2.value.name.equalsIgnoreCase(name)) match {
      case Some(value) =>
        if (isExpired(value._2)) {
          None
        } else {
          Some(value._2.value)
        }
      case None => None
    }
  }

  /**
    * Adds an object to the cache, if cache limit has been reached, then will remove the oldest
    * accessed item in the cache
    *
    * @param obj The object to add to the cache
    * @return The object put in the cache, or None if it could not be placed in the cache
    */
  def add(obj:Value) : Option[Value] = synchronized {
    if (cache.size == cacheLimit) {
      val oldestEntry = cache.valuesIterator.reduceLeft((x, y) => if (x.accessTime.isBefore(y.accessTime)) x else y)
      remove(oldestEntry.value.id)
    } else if (cache.size > cacheLimit) {
      // something has gone very wrong if the cacheLimit has already been exceeded, this really shouldn't ever happen
      // in this case we go for the nuclear option, basically blow away the whole cache
      cache.clear()
    }
    cache.put(obj.id, InnerValue(obj, new LocalDateTime())) match {
      case Some(value) => Some(value.value)
      case None => None
    }
  }

  /**
    * Removes an object from the cache based on the id
    *
    * @param id the id of the object to be removed
    * @return The object removed from the cache, or None if it could not be removed from the cache,
    *         or was not originally in the cache
    */
  def remove(id:Key) : Option[Value] = synchronized {
    cache.remove(id) match {
      case Some(value) => Some(value.value)
      case None => None
    }
  }

  /**
    * Remove an object from the cache based on the name
    *
    * @param name The name of the object to be removed
    * @return The object removed from the cache, or None if it could not be removed from the cache,
    *         or was not originally in the cache
    */
  def remove(name:String) : Option[Value] = synchronized {
    this.find(name) match {
      case Some(value) => this.remove(value.id)
      case None => None
    }
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

  private def isExpiredByKey(key:Key) : Boolean = synchronized {
    cache.get(key) match {
      case Some(value) => isExpired(value)
      case None => true
    }
  }

  private def isExpired(value:InnerValue[Key, Value]) : Boolean = synchronized {
    val currentTime = new LocalDateTime()
    if (currentTime.isAfter(value.accessTime.plus(Seconds.seconds(cacheExpiry)))) {
      remove(value.value.id.toString)
      true
    } else {
      false
    }
  }
}
