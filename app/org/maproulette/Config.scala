package org.maproulette

import javax.inject.{Inject, Singleton}

import org.maproulette.actions.Actions
import play.api.Application

/**
  * @author cuthbertm
  */
@Singleton
class Config @Inject() (implicit val application:Application) {
  lazy val logoURL = application.configuration.getString(Config.KEY_LOGO) match {
    case Some(logo) => logo
    case None => "/assets/images/logo.png"// default to the Map Roulette Icon
  }

  lazy val superKey : Option[String] = application.configuration.getString(Config.KEY_SUPER_KEY)

  lazy val superAccounts : List[String] = application.configuration.getString(Config.KEY_SUPER_ACCOUNTS) match {
    case Some(accs) => accs.split(",").toList
    case None => List.empty
  }

  def isDebugMode : Boolean =
    application.configuration.getBoolean(Config.KEY_DEBUG).getOrElse(false)

  def actionLevel : Int =
    application.configuration.getInt(Config.KEY_ACTION_LEVEL).getOrElse(Actions.ACTION_LEVEL_2)

  def numberOfChallenges : Int =
    application.configuration.getInt(Config.KEY_NUM_OF_CHALLENGES).getOrElse(Config.DEFAULT_NUM_OF_CHALLENGES)

  def numberOfActivities : Int =
    application.configuration.getInt(Config.KEY_RECENT_ACTIVITY).getOrElse(Config.DEFAULT_RECENT_ACTIVITY)
}

object Config {
  val KEY_LOGO = "maproulette.logo"
  val KEY_SUPER_KEY = "maproulette.super.key"
  val KEY_SUPER_ACCOUNTS = "maproulette.super.accounts"
  val KEY_DEBUG = "maproulette.debug"
  val KEY_ACTION_LEVEL = "maproulette.action.level"
  val KEY_NUM_OF_CHALLENGES = "maproulette.limits.challenges"
  val KEY_RECENT_ACTIVITY = "maproulette.limits.activities"

  val DEFAULT_NUM_OF_CHALLENGES = 3
  val DEFAULT_RECENT_ACTIVITY = 5
}
