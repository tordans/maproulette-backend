package org.maproulette.jobs

import javax.inject.Inject

import anorm._
import akka.actor.Actor
import play.api.Logger
import play.api.db.Database

/**
  * This job will update the locations of all the challenges periodically. (Once a day
  *
  * @author cuthbertm
  */
class LocationSchedulerActor @Inject() (db:Database) extends Actor {
  override def receive: Receive = {
    case "updateLocations" => updateLocations()
  }

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
