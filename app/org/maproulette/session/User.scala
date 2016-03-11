package org.maproulette.session

import java.util.UUID
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.maproulette.models.BaseObject
import org.maproulette.session.dal.UserDAL
import play.api.libs.Crypto
import play.api.libs.json.Json
import play.api.libs.oauth.RequestToken
import scala.xml.{XML, Elem}

/**
  * Classes for the User object and the OSM Profile
  *
  * @author cuthbertm
  */
/**
  * Basic Location case class defining longitude and latitude
  *
  * @param longitude longitude for location
  * @param latitude latitude for location
  */
case class Location(longitude:Double, latitude:Double)

/**
  * Information specific to the OSM profile of the user. All users in the system are based on
  * OSM users.
  *
  * @param id The osm id
  * @param displayName The display name for the osm user
  * @param description The description of the OSM user as per their OSM profile
  * @param avatarURL The avatar URL to enabling displaying of their avatar
  * @param homeLocation Their home location
  * @param created When their OSM account was created
  * @param requestToken The key and secret (access token) used for authorization
  */
case class OSMProfile(id:Long,
                      displayName:String,
                      description:String,
                      avatarURL:String,
                      homeLocation:Location,
                      created:DateTime,
                      requestToken: RequestToken)

/**
  * Information specific to the Map Roulette user.
  *
  * @param id The id defined by the database
  * @param created When their account was created
  * @param modified When their account was last modified. If last modified was longer then a day,
  *                 will automatically update their OSM information
  * @param theme The theme to display in Map Roulette. Optionally - skin-blue, skin-blue-light,
  *              skin-black, skin-black-light, skin-purple, skin-purple-light, skin-yellow,
  *              skin-yellow-light, skin-red, skin-red-light, skin-green, skin-green-light
  * @param osmProfile The osm profile information
  * @param apiKey The current api key to validate requests
  * @param guest Whether this is a guest account or not.
  */
case class User(override val id:Long,
                created:DateTime,
                modified:DateTime,
                theme:String,
                osmProfile: OSMProfile,
                apiKey:Option[String]=None,
                guest:Boolean=false) extends BaseObject[Long] {
  // for users the display name is always retrieved from OSM
  override def name = osmProfile.displayName

  def formattedOSMCreatedDate = DateTimeFormat.forPattern("MMMM. yyyy").print(osmProfile.created)
  def formattedMPCreatedDate = DateTimeFormat.forPattern("MMMM. yyyy").print(created)

  /**
    * Generate (or regenerate) api key for the user, set it in the database
    */
  def generateAPIKey : User = {
    val newAPIKey = Crypto.encryptAES(id + "|" + UUID.randomUUID())
    UserDAL.update(Json.parse(s"""{"apiKey":"$newAPIKey"}"""))(id)
    this.copy(apiKey = Some(newAPIKey))
  }
}

/**
  * Static functions to easily create user objects
  */
object User {

  /**
    * Generate a User object based on the XML details and request token
    *
    * @param root The XML details of the user based on OSM details API
    * @param requestToken The access token used to retrieve the OSM details
    * @return A user object based on the XML details provided
    */
  def apply(root:Elem, requestToken:RequestToken) : User = {
    val userXML = (root \ "user").head
    val displayName = userXML \@ "display_name"
    val osmAccountCreated = userXML \@ "account_created"
    val osmId = userXML \@ "id"
    val description = (userXML \ "description").head.text
    val avatarURL = (userXML \ "img").head \@ "href"
    // todo currently setting to 0,0 lat,lon but will need to set a default or null or something
    val location = (userXML \ "home").headOption match {
      case Some(location) => Location((location \@ "lat").toDouble, (location \@ "lon").toDouble)
      case None => Location(0, 0)
    }
    User(-1, null, null, "skin-blue", OSMProfile(osmId.toLong,
      displayName,
      description,
      avatarURL,
      location,
      DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").parseDateTime(osmAccountCreated),
      requestToken
    ))
  }

  /**
    * Generates a User object based on the json details and request token
    *
    * @param userXML A XML string originally queried form the OSM details API
    * @param requestToken The access token used to retrieve the OSM details
    * @return A user object based on the XML details provided
    */
  def apply(userXML:String, requestToken: RequestToken) : User =
    apply(XML.loadString(userXML), requestToken)

  /**
    * Creates a guest user object with default information.
    */
  val guestUser = User(0, DateTime.now(), DateTime.now(), "skin-blue",
    OSMProfile(0, "Guest",
      "Sign in using your OSM account for more access to Map Roulette features.",
      "assets/images/user_no_image.png",
      Location(47.6097, 122.3331),
      DateTime.now(),
      RequestToken("", "")
    ), None, true
  )

  /**
    * Simple helper function that if the provided Option[User] is None, will return a guest
    * user, otherwise will simply return back the provided user
    *
    * @param user The user to check
    * @return Guest user if none, otherwise simply the provided user.
    */
  def userOrMocked(user:Option[User]) : User = {
    user match {
      case Some(u) => u
      case None => guestUser
    }
  }
}
