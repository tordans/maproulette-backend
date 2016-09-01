// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.jobs

import javax.inject.{Inject, Singleton}

import akka.actor.Actor
import akka.actor.Actor.Receive
import play.api.Logger
import play.api.db.Database
import anorm._

/**
  * The main actor that handles all scheduled activities
  *
  * @author cuthbertm
  * @author davis_20
  */
@Singleton
class SchedulerActor @Inject() (db:Database) extends Actor {
  override def receive: Receive = {
    case "cleanLocks" => this.cleanLocks()
    case "runChallengeSchedules" => this.runChallengeSchedules()
    case "updateLocations" => this.updateLocations()
  }

  /**
    * This job will remove all stale locks from the system. A stale lock is a lock that has maintained
    * the lock for over an hour.
    */
  def cleanLocks() : Unit = {
    Logger.debug("Running the clean locks job now...")
    this.db.withTransaction { implicit c =>
      val locksDeleted = SQL"""DELETE FROM locked WHERE AGE(NOW(), locked_time) > '1 hour'""".executeUpdate()
      Logger.debug(s"$locksDeleted were found and deleted.")
    }
  }

  /**
    * This job will update the challenges from remote geojson or overpass query based on the supplied
    * schedules in the challenge
    */
  def runChallengeSchedules() : Unit = {
    Logger.debug("Running the challenge schedules job now...")
  }


  /**
    * This job will update the locations of all the challenges periodically. (Once a day)
    */
  def updateLocations() : Unit = {
    Logger.debug("Updating challenge locations done...")
    db.withTransaction { implicit c =>
      val query = """DO $$
                      DECLARE
                        rec RECORD;
                      BEGIN
                        FOR rec IN SELECT id FROM challenges LOOP
                          UPDATE challenges SET location = (SELECT ST_Centroid(ST_Collect(ST_Makevalid(location)))
                                  FROM tasks
                                  WHERE parent_id = rec.id)
                          WHERE id = rec.id;
                        END LOOP;
                      END$$;"""

      SQL(query).executeUpdate()
    }
    Logger.debug("Completed updating challenge locations.")
  }
}
