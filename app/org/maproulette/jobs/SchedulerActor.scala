package org.maproulette.jobs

import javax.inject.{Inject, Singleton}

import akka.actor.Actor
import akka.actor.Actor.Receive
import play.api.Logger
import play.api.db.Database
import anorm._

/**
  * This job will remove all stale locks from the system. A stale lock is a lock that has maintained
  * the lock for over an hour.
  *
  * @author cuthbertm
  */
@Singleton
class SchedulerActor @Inject() (db:Database) extends Actor {
  override def receive: Receive = {
    case "cleanLocks" => cleanLocks()
  }

  def cleanLocks() : Unit = {
    Logger.debug("Running the clean locks job now...")
    db.withTransaction { implicit c =>
      val locksDeleted = SQL"""DELETE FROM locked WHERE AGE(NOW(), locked_time) > '1 hour'""".executeUpdate()
      Logger.debug(s"$locksDeleted were found and deleted.")
    }
  }
}
