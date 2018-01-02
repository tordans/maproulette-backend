// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.cache

import java.util.concurrent.locks.{ReentrantReadWriteLock, ReadWriteLock}
import javax.inject.{Provider, Inject, Singleton}

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
class TagCacheManager @Inject() (tagDAL: Provider[TagDAL], db:Database) extends CacheManager[Long, Tag] {

  private val loadingLock:ReadWriteLock = new ReentrantReadWriteLock()

  def reloadTags : Unit = {
    this.loadingLock.writeLock().lock()
    try {
      db.withConnection { implicit c =>
        this.cache.clear()
        SQL"""SELECT * FROM tags""".as(tagDAL.get.parser.*).foreach(tag => cache.addObject(tag))
      }
    } finally {
      this.loadingLock.writeLock().unlock()
    }
  }
}
