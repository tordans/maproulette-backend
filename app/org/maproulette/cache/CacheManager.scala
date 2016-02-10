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
}
