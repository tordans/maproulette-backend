package org.maproulette.cache

import org.maproulette.data.BaseObject

/**
  * @author cuthbertm
  */
class CacheManager[Key, A<:BaseObject[Key]] {
  val cache = new CacheStorage[Key, A]()


  /**
    * Hits the cache first to see if the object exists, if it doesn't then it will hit the database and retrieve the object
    * If found will put the object in the cache
    *
    * @param id The id of the object, if None then will not hit the cache first
    * @param block the function to be executed that will retrieve the object to cache
    * @return An object base object that is cached
    */
  def withOptionCaching(block:() => Option[A])
                       (implicit id:Option[Key]=None, caching:Boolean=true): Option[A] = {
    caching match {
      case true =>
        val cached = id match {
          case Some(key) => cache.get(key)
          case None => None
        }

        cached match {
          case Some(hit) => Some(hit)
          case None =>
            block() match {
              case Some(result) =>
                cache.add(result)
                Some(result)
              case None => None
            }
        }
      case false => block()
    }
  }

  def withUpdatingCache(retrieve:Key => Option[A])(block:A => Option[A])
                       (implicit id:Key, caching:Boolean=true) : Option[A] = {
    val cachedItem = caching match {
      case true =>
        cache.get(id) match {
          case Some(hit) => Some(hit)
          case None => retrieve(id) match {
            case Some(value) =>
              Some(value)
            case None => None
          }
        }
      case false => retrieve(id)
    }

    cachedItem match {
      case Some(item) =>
        block(item) match {
          case Some(updatedItem) =>
            if (caching) cache.add(updatedItem)
            Some(updatedItem)
          case None => None
        }
      case None => None
    }
  }

  /**
    * This will pull the items out of the cache that have been cached, and send the rest of the id's
    * to the database to retrieve the objects and place them in the cache after they have been retrieved
    *
    * @param block the lambda function that will be used to retrieve the objects
    * @param ids The ids of the objects to retrieve
    * @return A list of objects
    */
  def withIDListCaching(block:List[Key] => List[A])
                       (implicit ids:List[Key]=List(), caching:Boolean=true) : List[A] = {
    caching match {
      case true =>
        val connected = ids.map(id => (id, cache.get(id)))
        val unCachedIDs = connected.filter(value => value._2.isEmpty).map(_._1)
        // we execute the block if there are uncachedID's or if the original ids passed in is empty
        val cachedItems = if (unCachedIDs.nonEmpty || ids.isEmpty) {
          val uncachedList = block(unCachedIDs)
          (unCachedIDs zip uncachedList).foreach(obj => cache.add(obj._2))
          uncachedList ++ connected.flatMap(_._2)
        } else {
          connected.flatMap(_._2)
        }
        cachedItems.filter(entry => ids.isEmpty || unCachedIDs.contains(entry.id)).foreach(cache.add(_))
        cachedItems
      case false => block(ids)
    }
  }

  def withCacheIDDeletion[B](block:() => B)(implicit ids:List[Key], caching:Boolean=true) : B = {
    if (caching) ids.foreach(cache.remove(_))
    block()
  }

  def getByName(name:String) : Option[A] = cache.find(name)

  def deleteByName(name:String) : Option[A] = cache.find(name) match {
    case Some(obj) => cache.remove(obj.id)
    case None => None
  }

  def withNameListCaching(block:List[String] => List[A])
                         (implicit names:List[String]=List(), caching:Boolean=true) : List[A] = {
    caching match {
      case true =>
        val connected = names.map(name => (name, cache.find(name)))
        val unCachedIDs = connected.filter(value => value._2.isEmpty).map(_._1)
        if (unCachedIDs.nonEmpty) {
          val list = block(unCachedIDs)
          list.foreach(obj => cache.add(obj))
          list ++ connected.flatMap(_._2)
        } else {
          connected.flatMap(_._2)
        }
      case false => block(names)
    }
  }

  def withCacheNameDeletion[B](block:() => B)
                              (implicit ids:List[String], caching:Boolean=true) : B = {
    if (caching) ids.foreach(cache.remove(_))
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
  def withCreatingCache(retrieve:Key => Option[A], create:A => A)(implicit item:A, caching:Boolean=true) : A = {
    implicit val id = item.id
    withUpdatingCache(retrieve) { implicit cached =>
      Some(cached)
    } match {
      case Some(a) => a
      case None =>
        val created = create(item)
        if (caching) cache.add(created)
        created
    }
  }
}
