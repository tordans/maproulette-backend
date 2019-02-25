// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.cache

import org.joda.time.{LocalDateTime, Seconds}

import scala.collection.mutable.Map

case class BasicInnerValue[Key, Value](key: Key, value: Value, accessTime: LocalDateTime, localExpiry: Option[Int] = None)

/**
  * This is a very basic Cache Storage class that will store all items in memory. Ultimately this
  * class should be extended to use the Play Cache API
  *
  * cacheLimit - The number of entries allowed in the cache until it starts kicking out the oldest entries
  * cacheExpiry - The number of seconds allowed until a cache item is expired out the cache lazily
  *
  * @author cuthbertm
  */
class BasicCache[Key, Value](cacheLimit: Int = CacheManager.DEFAULT_CACHE_LIMIT, cacheExpiry: Int = CacheManager.DEFAULT_CACHE_EXPIRY) {
  val cache: Map[Key, BasicInnerValue[Key, Value]] = Map.empty

  /**
    * Gets an object from the cache
    *
    * @param id The id of the object you are looking for
    * @return The object from the cache, None if not found
    */
  def get(id: Key): Option[Value] = synchronized {
    this.cache.get(id) match {
      case Some(value) =>
        if (isExpired(value)) {
          None
        } else {
          // because it has been touched, we need to update the accesstime
          add(id, value.value)
          Some(value.value)
        }
      case None => None
    }
  }

  /**
    * Adds an object to the cache, if cache limit has been reached, then will remove the oldest
    * accessed item in the cache
    *
    * @param obj         The object to add to the cache
    * @param localExpiry You can add a custom expiry to a specific element in seconds
    * @return The object put in the cache, or None if it could not be placed in the cache
    */
  def add(key: Key, obj: Value, localExpiry: Option[Int] = None): Option[Value] = synchronized {
    if (this.cache.size == cacheLimit) {
      val oldestEntry = this.cache.valuesIterator.reduceLeft((x, y) => if (x.accessTime.isBefore(y.accessTime)) x else y)
      remove(oldestEntry.key)
    } else if (this.cache.size > cacheLimit) {
      // something has gone very wrong if the cacheLimit has already been exceeded, this really shouldn't ever happen
      // in this case we go for the nuclear option, basically blow away the whole cache
      this.cache.clear()
    }
    this.cache.put(key, BasicInnerValue(key, obj, new LocalDateTime(), localExpiry)) match {
      case Some(value) => Some(value.value)
      case None => None
    }
  }

  /**
    * Checks to see if the item has expired and should be removed from the cache. If it finds the
    * item and it has expired it will automatically remove it from the cache.
    *
    * @param value The value to check in the cache
    * @return true if it doesn't exist or has expired
    */
  protected def isExpired(value: BasicInnerValue[Key, Value]): Boolean = synchronized {
    val currentTime = new LocalDateTime()
    val itemExpiry = value.localExpiry match {
      case Some(v) => v
      case None => cacheExpiry
    }
    if (currentTime.isAfter(value.accessTime.plus(Seconds.seconds(itemExpiry)))) {
      remove(value.key)
      true
    } else {
      false
    }
  }

  /**
    * Removes an object from the cache based on the id
    *
    * @param id the id of the object to be removed
    * @return The object removed from the cache, or None if it could not be removed from the cache,
    *         or was not originally in the cache
    */
  def remove(id: Key): Option[Value] = synchronized {
    this.cache.remove(id) match {
      case Some(value) => Some(value.value)
      case None => None
    }
  }

  /**
    * Fully clears the cache, this may not be applicable for non basic in memory caches
    */
  def clear(): Unit = this.cache.clear()

  /**
    * The current size of the cache
    *
    * @return
    */
  def size: Int = this.cache.size

  /**
    * True size is a little bit more accurate than size, however the performance will be a bit slower
    * as this size will loop through all the objects in the cache and expire out any items that have
    * expired. Thereby giving the true size at the end.
    *
    * @return
    */
  def trueSize: Int = this.cache.keysIterator.count(!isExpiredByKey(_))

  private def isExpiredByKey(key: Key): Boolean = synchronized {
    this.cache.get(key) match {
      case Some(value) => isExpired(value)
      case None => true
    }
  }

  /**
    * Checks if an item is cached or not
    *
    * @param id The id of the object to check to see if it is in the cache
    * @return true if the item is found in the cache
    */
  def isCached(id: Key): Boolean = this.cache.contains(id)
}
