package org.maproulette.jobs

import javax.inject.{Inject, Singleton}

import anorm._
import play.api.db.Database
import play.api.inject.ApplicationLifecycle

import scala.concurrent.Future

/**
  * @author mcuthbert
  */
@Singleton
class Bootstrap @Inject()(appLifeCycle:ApplicationLifecycle, db:Database) {

  def start(): Unit = {
    // for startup we make sure that all the super users are set correctly
    db.withConnection { implicit c =>
      SQL"""DELETE FROM user_groups
            WHERE group_id = -999 AND NOT osm_user_id = -999
        """.executeUpdate()
    }
  }

  appLifeCycle.addStopHook { () =>
    Future.successful(())
  }

  start()
}
