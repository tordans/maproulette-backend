package org.maproulette.data

import javax.inject.{Inject, Singleton}

import anorm._
import anorm.SqlParser._
import org.maproulette.Config
import org.maproulette.models.{Challenge, Project}
import org.maproulette.models.utils.DALHelper
import play.api.Application
import play.api.db.Database

/**
  * @author cuthbertm
  */
@Singleton
class DataManager @Inject()(config: Config, db:Database)(implicit application:Application) extends DALHelper {

  /**
    * Gets a basic challenge summary that contains information regarding all the tasks for a given
    * challenge and the status for those tasks.
    *
    * @param challenge The challenge to get the information for
    * @return A map of status id's corresponding to number of tasks for that status
    */
  def getChallengeSummary(challenge:Challenge) : Map[Int, Int] = {
    db.withConnection { implicit c =>
      val parser = for {
        count <- int("count")
        status <- int("status")
      } yield status -> count
      SQL"""SELECT COUNT(*) AS count, status FROM tasks WHERE parent_id = ${challenge.id} GROUP BY status"""
        .as(parser.*).toMap
    }
  }

  def getProjectSummary(project:Project) = {

  }
}
