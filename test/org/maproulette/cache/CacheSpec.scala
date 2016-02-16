package org.maproulette.cache

import org.junit.runner.RunWith
import org.maproulette.data.BaseObject
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.collection.mutable

/**
  * @author cuthbertm
  */
case class TestBaseObject(override val id:Long, override val name:String) extends BaseObject[Long]

@RunWith(classOf[JUnitRunner])
class CacheSpec extends Specification {
  implicit val manager = new CacheManager[Long, TestBaseObject]
  val theCache = manager.cache

  sequential

  "CacheManager" in {
    "cache element withOptionCaching" in {
      theCache.clear
      cacheObject(25, "test")
      val cachedObj = theCache.get(25)
      cachedObj.isDefined mustEqual true
      cachedObj.get.name mustEqual "test"
      cachedObj.get.id mustEqual 25
    }

    "delete cached element withCacheIDDeletion" in {
      theCache.clear
      cacheObject(1, "name1")
      implicit val ids = List(1L)
      manager.withCacheIDDeletion { () =>
        val cachedObj = theCache.get(1)
        cachedObj.isEmpty mustEqual true
      }
    }

    "delete cached elements withCacheIDDeletion" in {
      theCache.clear
      cacheObject(1, "name1")
      cacheObject(2, "name2")
      cacheObject(3, "name3")
      implicit val ids = List(1L, 2L)
      manager.withCacheIDDeletion { () =>
        theCache.get(1).isEmpty mustEqual true
        theCache.get(2).isEmpty mustEqual true
        theCache.get(3).isDefined mustEqual true
      }
    }

    "delete cached elements withCacheNameDeletion" in {
      theCache.clear
      cacheObject(1, "name1")
      cacheObject(2, "name2")
      cacheObject(3, "name3")
      implicit val names = List("name1", "name2")
      manager.withCacheNameDeletion { () =>
        theCache.find("name1").isEmpty mustEqual true
        theCache.find("name2").isEmpty mustEqual true
        theCache.find("name3").isDefined mustEqual true
      }
    }

    "cache multiple elements withIDListCaching" in {
      theCache.clear
      implicit val cacheList = List(1L, 2L, 3L)
      manager.withIDListCaching { implicit uncachedList =>
        uncachedList.size mustEqual 3
        uncachedList.map(id => TestBaseObject(id, s"name$id"))
      }
      theCache.get(1).isDefined mustEqual true
      theCache.get(2).isDefined mustEqual true
      theCache.get(3).isDefined mustEqual true
    }

    "cache multiple elements withNameListCaching" in {
      theCache.clear
      implicit val cacheNames = List("name1", "name2", "name3")
      manager.withNameListCaching { implicit uncachedList =>
        uncachedList.size mustEqual 3
        uncachedList.map(name => TestBaseObject(name.charAt(4).toString.toInt, name))
      }
      theCache.get(1).isDefined mustEqual true
      theCache.get(2).isDefined mustEqual true
      theCache.get(3).isDefined mustEqual true
    }

    "caching must be able to be disabled" in {
      theCache.clear
      implicit val caching = false
      manager.withOptionCaching { () => Some(TestBaseObject(1, "name1"))}
      theCache.size mustEqual 0
    }

    "cache only the elements that are not already cached withIDListCaching" in {
      theCache.clear
      cacheObject(1, "name1")
      cacheObject(2, "name2")
      cacheObject(3, "name3")
      implicit val ids = List(2L, 3L, 5L, 6L)
      manager.withIDListCaching { implicit uncachedList =>
        uncachedList.size mustEqual 2
        uncachedList.map(id => TestBaseObject(id, s"name$id"))
      }
      theCache.size mustEqual 5
      theCache.get(5).isDefined mustEqual true
      theCache.get(6).isDefined mustEqual true
    }

    "cache updated changes for element" in {
      implicit val id = 1L
      theCache.clear
      cacheObject(id, "name1")
      manager.withUpdatingCache(fakeRetrieve) { implicit cached =>
        Some(TestBaseObject(1, "name2"))
      }
      val cachedObj = theCache.get(1)
      cachedObj.isDefined mustEqual true
      cachedObj.get.name mustEqual "name2"
    }

    "cache updated changes for element and retrieve from supplied function" in {
      implicit val id = 3L
      theCache.clear
      manager.withUpdatingCache(fakeRetrieve) { implicit cached =>
        cached.name mustEqual "testObject"
        Some(TestBaseObject(3, "updated"))
      }
      val cachedObj = theCache.get(3)
      cachedObj.isDefined mustEqual true
      cachedObj.get.name mustEqual "updated"
    }
  }

  private def fakeRetrieve(id:Long) : Option[TestBaseObject] = Some(TestBaseObject(3, "testObject"))

  private def cacheObject(id:Long, name:String) =
    manager.withOptionCaching { () => Some(TestBaseObject(id, name))}
}
