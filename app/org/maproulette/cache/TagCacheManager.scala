package org.maproulette.cache

import java.util.concurrent.locks.{ReentrantReadWriteLock, ReadWriteLock}
import javax.inject.{Inject, Singleton}

import anorm._
import org.maproulette.models.Tag
import org.maproulette.models.dal.TagDAL
import play.api.db.{Database, DB}
import play.api.Play.current

/**
  * This is not currently a real cache manager, it will just store elements that have been loaded into memory.
  * Using this too much will most likely end up in OutOfMemory exceptions, so this needs to be readdressed
  * prior to a live version of this service. The CacheStorage is the area that you would be required
  * to be modified.
  *
  * @author cuthbertm
  */
@Singleton
class TagCacheManager @Inject() (tagDAL: TagDAL, db:Database) extends CacheManager[Long, Tag] {

  private val loadingLock:ReadWriteLock = new ReentrantReadWriteLock()

  // TODO: This is not the correct approach to just load everything into memory - this needs to be redone later
  def reloadTags = {
    loadingLock.writeLock().lock()
    try {
      db.withConnection { implicit c =>
        cache.clear()
        SQL"""SELECT * FROM tags""".as(tagDAL.parser.*).foreach(tag => cache.add(tag))
      }
    } finally {
      loadingLock.writeLock().unlock()
    }
  }
}
