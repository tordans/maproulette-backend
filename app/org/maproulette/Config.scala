package org.maproulette

import org.maproulette.actions.Actions
import play.api.Play.current

/**
  * @author cuthbertm
  */
object Config {
  val KEY_DEBUG = "maproulette.debug"
  val KEY_ACTION_LEVEL = "maproulette.action.level"

  def isDebugMode : Boolean = current.configuration.getBoolean(KEY_DEBUG).getOrElse(false)

  def actionLevel : Int = current.configuration.getInt(KEY_ACTION_LEVEL).getOrElse(Actions.ACTION_LEVEL_2)
}
