package org.maproulette.cache

import org.maproulette.data.BaseObject

/**
  * @author cuthbertm
  */
trait CacheManager[A <: BaseObject] extends BaseCacheManager[A] {

  def getByName(name:String) : Option[A] = cache.find(element => element._2.name.equalsIgnoreCase(name)).map(tag => tag._2)

  def autoAdd(obj:A) = if (obj.id > -1) cache.put(obj.id+"", obj)

  def deleteByName(name:String) : Option[A] = cache.find(element => element._2.name.equalsIgnoreCase(name)) match {
    case Some(obj) => cache.remove(obj._2.id+"")
    case None => None
  }

  def withNameListCaching(block:List[String] => List[A])
                            (implicit names:List[String]=List(), caching:Boolean=true) : List[A] = {
    caching match {
      case true =>
        val connected = names.map(name => (name, this.get(name)))
        val unCachedIDs = connected.filter(value => value._2.isEmpty).map(_._1)
        if (unCachedIDs.nonEmpty) {
          val list = block(unCachedIDs)
          list.foreach(obj => this.autoAdd(obj))
          list ++ connected.flatMap(_._2)
        } else {
          connected.flatMap(_._2)
        }
      case false => block(names)
    }
  }

  def withCacheNameDeletion[B](block:() => B)
                           (implicit ids:List[String], caching:Boolean=true) : B = {
    if (caching) ids.foreach(this.delete(_))
    block()
  }

  /**
    * This will attempt to retrieve the item, and if it cannot it will create it and dump it in the cache
    * then return the item
    *
    * @param retrieve The function that is used to the retrieve the item if it is not in the cache
    * @param create The function to create the item if it is not found
    * @param item The item to find/create
    * @param caching Whether caching is turned on or off
    * @return
    */
  def withCreatingCache(retrieve:Long => Option[A], create:A => A)(implicit item:A, caching:Boolean=true) : A = {
    implicit val id = item.id
    withUpdatingCache(retrieve) { implicit cached =>
      Some(cached)
    } match {
      case Some(a) => a
      case None =>
        val created = create(item)
        if (caching) autoAdd(created)
        created
    }
  }
}
