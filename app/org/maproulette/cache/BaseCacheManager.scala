package org.maproulette.cache

import scala.collection.mutable.Map

/**
  * @author cuthbertm
  */
trait BaseCacheManager[A] {
  // TODO: replace this later with Play's internal cache
  implicit protected val cache:Map[String, A]

  def get(id:String) : Option[A] = cache.get(id)

  def get(id:Long) : Option[A] = get(id+"")

  def add(id:String, obj:A) : Option[A] = cache.put(id, obj)

  def add(id:Long, obj:A) : Option[A] = add(id+"", obj)

  def delete(id:String) : Option[A] = cache.remove(id)

  def delete(id:Long) : Option[A] = delete(id+"")

  def clear = cache.clear()

  def size : Int = cache.size

  /**
    * Hits the cache first to see if the object exists, if it doesn't then it will hit the database and retrieve the object
    * If found will put the object in the cache
    *
    * @param id The id of the object, if None then will not hit the cache first
    * @param block the function to be executed that will retrieve the object to cache
    * @return An object base object that is cached
    */
  def withOptionCaching(block:() => Option[A])
                       (implicit id:Long=(-1), caching:Boolean=true): Option[A] = {
    caching match {
      case true =>
        this.get(id) match {
          case Some(hit) => Some(hit)
          case None =>
            block() match {
              case Some(result) =>
                this.add(id, result)
                Some(result)
              case None => None
            }
        }
      case false => block()
    }
  }

  def withUpdatingCache(retrieve:Long => Option[A])(block:A => Option[A])
                       (implicit id:Long, caching:Boolean=true) : Option[A] = {
    val cachedItem = caching match {
      case true =>
        this.get(id) match {
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
            if (caching) this.add(id, updatedItem)
            Some(updatedItem)
          case None => None
        }
      case None => None
    }
  }

  /**
    * This will pull the items out of the cache that have been cached, and send the rest of the id's
    * to the database to retireve the objects and place them in the cache after they have been retrieved
    *
    * @param block the lambda function that will be used to retrieve the objects
    * @param ids The ids of the objects to retrieve
    * @return A list of objects
    */
  def withIDListCaching(block:List[Long] => List[A])
                       (implicit ids:List[Long]=List(), caching:Boolean=true) : List[A] = {
    caching match {
      case true =>
        val connected = ids.map(id => (id, this.get(id)))
        val unCachedIDs = connected.filter(value => value._2.isEmpty).map(_._1)
        if (unCachedIDs.nonEmpty) {
          val uncachedList = block(unCachedIDs)
          (unCachedIDs zip uncachedList).foreach(obj => this.add(obj._1, obj._2))
          uncachedList ++ connected.flatMap(_._2)
        } else {
          connected.flatMap(_._2)
        }
      case false => block(ids)
    }
  }

  def withCacheIDDeletion[B](block:() => B)(implicit ids:List[Long], caching:Boolean=true) : B = {
    if (caching) ids.foreach(this.delete(_))
    block()
  }
}
