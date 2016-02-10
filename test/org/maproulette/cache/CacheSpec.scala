package org.maproulette.cache

import org.junit.runner.RunWith
import org.maproulette.data.BaseObject
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.collection.mutable

/**
  * @author cuthbertm
  */
case class TestBaseObject(override val id:Long, override val name:String) extends BaseObject

object TestCacheManager extends CacheManager[TestBaseObject] {
  override implicit protected val cache = mutable.Map[String, TestBaseObject]().empty
}

@RunWith(classOf[JUnitRunner])
class CacheSpec extends Specification {
  implicit val manager = TestCacheManager

  sequential

  "CacheManager" in {
    "cache element withOptionCaching" in {
      manager.clear
      cacheObject(25, "test")
      val cachedObj = TestCacheManager.get(25)
      cachedObj.isDefined mustEqual true
      cachedObj.get.name mustEqual "test"
      cachedObj.get.id mustEqual 25
    }

    "delete cached element withCacheIDDeletion" in {
      manager.clear
      cacheObject(1, "name1")
      implicit val ids = List(1L)
      manager.withCacheIDDeletion { () =>
        val cachedObj = TestCacheManager.get(1)
        cachedObj.isEmpty mustEqual true
      }
    }

    "delete cached elements withCacheIDDeletion" in {
      manager.clear
      cacheObject(1, "name1")
      cacheObject(2, "name2")
      cacheObject(3, "name3")
      implicit val ids = List(1L, 2L)
      manager.withCacheIDDeletion { () =>
        TestCacheManager.get(1).isEmpty mustEqual true
        TestCacheManager.get(2).isEmpty mustEqual true
        TestCacheManager.get(3).isDefined mustEqual true
      }
    }

    "delete cached elements withCacheNameDeletion" in {
      manager.clear
      cacheObject(1, "name1")
      cacheObject(2, "name2")
      cacheObject(3, "name3")
      implicit val names = List("name1", "name2")
      manager.withCacheNameDeletion { () =>
        TestCacheManager.get("name1").isEmpty mustEqual true
        TestCacheManager.get("name2").isEmpty mustEqual true
        TestCacheManager.get("name3").isDefined mustEqual true
      }
    }

    "cache multiple elements withIDListCaching" in {
      manager.clear
      implicit val cacheList = List(1L, 2L, 3L)
      manager.withIDListCaching { implicit uncachedList =>
        uncachedList.size mustEqual 3
        uncachedList.map(id => TestBaseObject(id, s"name$id"))
      }
      TestCacheManager.get(1).isDefined mustEqual true
      TestCacheManager.get(2).isDefined mustEqual true
      TestCacheManager.get(3).isDefined mustEqual true
    }

    "cache multiple elements withNameListCaching" in {
      manager.clear
      implicit val cacheNames = List("name1", "name2", "name3")
      manager.withNameListCaching { implicit uncachedList =>
        uncachedList.size mustEqual 3
        uncachedList.map(name => TestBaseObject(name.charAt(4).toString.toInt, name))
      }
      TestCacheManager.get(1).isDefined mustEqual true
      TestCacheManager.get(2).isDefined mustEqual true
      TestCacheManager.get(3).isDefined mustEqual true
    }

    "caching must be able to be disabled" in {
      manager.clear
      implicit val caching = false
      manager.withOptionCaching { () => Some(TestBaseObject(1, "name1"))}
      TestCacheManager.size mustEqual 0
    }

    "cache only the elements that are not already cached withIDListCaching" in {
      manager.clear
      cacheObject(1, "name1")
      cacheObject(2, "name2")
      cacheObject(3, "name3")
      implicit val ids = List(2L, 3L, 5L, 6L)
      manager.withIDListCaching { implicit uncachedList =>
        uncachedList.size mustEqual 2
        uncachedList.map(id => TestBaseObject(id, s"name$id"))
      }
      TestCacheManager.size mustEqual 5
      TestCacheManager.get(5).isDefined mustEqual true
      TestCacheManager.get(6).isDefined mustEqual true
    }

    "cache updated changes for element" in {
      implicit val id = 1L
      manager.clear
      cacheObject(id, "name1")
      manager.withUpdatingCache(fakeRetrieve) { implicit cached =>
        Some(TestBaseObject(1, "name2"))
      }
      val cachedObj = TestCacheManager.get(1)
      cachedObj.isDefined mustEqual true
      cachedObj.get.name mustEqual "name2"
    }

    "cache updated changes for element and retrieve from supplied function" in {
      implicit val id = 3L
      manager.clear
      manager.withUpdatingCache(fakeRetrieve) { implicit cached =>
        cached.name mustEqual "testObject"
        Some(TestBaseObject(3, "updated"))
      }
      val cachedObj = TestCacheManager.get(3)
      cachedObj.isDefined mustEqual true
      cachedObj.get.name mustEqual "updated"
    }
  }

  private def fakeRetrieve(id:Long) : Option[TestBaseObject] = Some(TestBaseObject(3, "testObject"))

  private def cacheObject(id:Long, name:String) =
    manager.withOptionCaching { () => Some(TestBaseObject(id, name))}
}
