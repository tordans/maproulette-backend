package org.maproulette.cache

import org.maproulette.data.Task

import scala.collection.mutable

/**
  * @author cuthbertm
  */
object TaskCacheManager extends CacheManager[Task] {
  override implicit protected val cache: mutable.Map[String, Task] = mutable.Map().empty
}
