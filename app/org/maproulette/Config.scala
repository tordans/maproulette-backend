package org.maproulette

import javax.inject.{Inject, Singleton}

import org.maproulette.actions.Actions
import play.api.Application
import play.api.libs.oauth.ConsumerKey

import scala.concurrent.duration.Duration

case class OSMOAuth(userDetailsURL:String, requestTokenURL:String, accessTokenURL:String,
                    authorizationURL:String, consumerKey:ConsumerKey)

case class OSMQLProvider(providerURL:String, requestTimeout:Duration)

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

  def getOSMOauth : OSMOAuth = {
    val osmServer = application.configuration.getString(Config.KEY_OSM_SERVER).get
    OSMOAuth(
      osmServer + application.configuration.getString(Config.KEY_OSM_USER_DETAILS_URL).get,
      osmServer + application.configuration.getString(Config.KEY_OSM_REQUEST_TOKEN_URL).get,
      osmServer + application.configuration.getString(Config.KEY_OSM_ACCESS_TOKEN_URL).get,
      osmServer + application.configuration.getString(Config.KEY_OSM_AUTHORIZATION_URL).get,
      ConsumerKey(application.configuration.getString(Config.KEY_OSM_CONSUMER_KEY).get,
        application.configuration.getString(Config.KEY_OSM_CONSUMER_SECRET).get)
    )
  }

  def getOSMQLProvider : OSMQLProvider = OSMQLProvider(
    application.configuration.getString(Config.KEY_OSM_QL_PROVIDER).get,
    Duration(application.configuration.getInt(Config.KEY_OSM_QL_TIMEOUT).getOrElse(Config.DEFAULT_OSM_QL_TIMEOUT), "s")
  )
}

object Config {
  val KEY_LOGO = "maproulette.logo"
  val KEY_SUPER_KEY = "maproulette.super.key"
  val KEY_SUPER_ACCOUNTS = "maproulette.super.accounts"
  val KEY_DEBUG = "maproulette.debug"
  val KEY_ACTION_LEVEL = "maproulette.action.level"
  val KEY_NUM_OF_CHALLENGES = "maproulette.limits.challenges"
  val KEY_RECENT_ACTIVITY = "maproulette.limits.activities"

  val KEY_OSM_SERVER = "osm.server"
  val KEY_OSM_USER_DETAILS_URL = "osm.userDetails"
  val KEY_OSM_REQUEST_TOKEN_URL = "osm.requestTokenURL"
  val KEY_OSM_ACCESS_TOKEN_URL = "osm.accessTokenURL"
  val KEY_OSM_AUTHORIZATION_URL = "osm.authorizationURL"
  val KEY_OSM_CONSUMER_KEY = "osm.consumerKey"
  val KEY_OSM_CONSUMER_SECRET = "osm.consumerSecret"

  val KEY_OSM_QL_PROVIDER = "osm.ql.provider"
  val KEY_OSM_QL_TIMEOUT = "osm.ql.timeout"

  val DEFAULT_OSM_QL_TIMEOUT = 25
  val DEFAULT_NUM_OF_CHALLENGES = 3
  val DEFAULT_RECENT_ACTIVITY = 5
}
