package org.maproulette.utils

import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.play.PlaySpec
import play.api.db.evolutions.Evolutions
import play.api.db.{Database, Databases}

/**
  * A little helper class to create and drop test tables
  *
  * @author mcuthbert
  */
trait TestDatabase extends PlaySpec with BeforeAndAfterAll {
  implicit val database: Database = Databases(
    "org.postgresql.Driver",
    "jdbc:postgresql://localhost:5433/mr_test",
    config = Map("username" -> "osm", "password" -> "osm")
  )

  override protected def beforeAll(): Unit = Evolutions.applyEvolutions(database)

  override protected def afterAll(): Unit = Evolutions.cleanupEvolutions(database)
}
