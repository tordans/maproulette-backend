package org.maproulette

import org.maproulette.actions.Actions
import play.api.Play.current

/**
  * @author cuthbertm
  */
object Config {
  val KEY_DEBUG = "maproulette.debug"
  val KEY_ACTION_LEVEL = "maproulette.action.level"

  lazy val logoURL = current.configuration.getString("maproulette.logo") match {
    case Some(logo) => logo
    case None => "assets/images/logo.png"// default to the Map Roulette Icon
  }

  def isDebugMode : Boolean = current.configuration.getBoolean(KEY_DEBUG).getOrElse(false)

  def actionLevel : Int = current.configuration.getInt(KEY_ACTION_LEVEL).getOrElse(Actions.ACTION_LEVEL_2)
}
