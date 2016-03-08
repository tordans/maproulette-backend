package org.maproulette.cache

import org.maproulette.models.BaseObject

import scala.collection.mutable.Map

/**
  * This is a very basic Cache Storage class that will store all items in memory. Ultimately this
  * class should be extends to use the Play Cache API
  *
  * @author cuthbertm
  */
class CacheStorage[Key, Value<:BaseObject[Key]] {
  protected val cache:Map[Key, Value] = Map.empty

  def get(id:Key) : Option[Value] = cache.get(id)

  def find(name:String) : Option[Value] =
    cache.find(element => element._2.name.equalsIgnoreCase(name)) match {
      case Some(value) => Some(value._2)
      case None => None
    }

  def add(obj:Value) : Option[Value] = cache.put(obj.id, obj)

  def remove(id:Key) : Option[Value] = cache.remove(id)

  def remove(name:String) : Option[Value] = this.find(name) match {
    case Some(value) => this.remove(value.id)
    case None => None
  }

  def clear() = cache.clear()

  def size : Int = cache.size

  def isCached(id:Key) : Boolean = cache.contains(id)
}
