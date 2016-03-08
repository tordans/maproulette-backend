package org.maproulette.session

import java.time.format.DateTimeFormatter
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.maproulette.models.BaseObject
import org.maproulette.session.dal.UserDAL
import play.api.libs.Crypto
import play.api.libs.json.Json
import play.api.libs.oauth.RequestToken
import scala.xml.{XML, Elem}

/**
  * @author cuthbertm
  */
case class Location(longitude:Double, latitude:Double)

case class OSMProfile(id:Long,
                      displayName:String,
                      description:String,
                      avatarURL:String,
                      homeLocation:Location,
                      created:DateTime,
                      requestToken: RequestToken)

case class User(override val id:Long,
                created:DateTime,
                osmProfile: OSMProfile,
                apiKey:Option[String]=None,
                guest:Boolean=false) extends BaseObject[Long] {
  // for users the display name is always retrieved from teh
  override def name = osmProfile.displayName

  def formattedOSMCreatedDate = DateTimeFormatter.ofPattern("MMMM. yyyy")
  def formattedMPCreatedDate = DateTimeFormatter.ofPattern("MMMM. yyyy")

  /**
    * Generate (or regenerate) api key for the user, set it in the database
    */
  def generateAPIKey : User = {
    val newAPIKey = Crypto.encryptAES(osmProfile.requestToken.token+"|"+osmProfile.requestToken.secret+"|"+id)
    UserDAL.update(Json.parse(s"""{"apiKey":"$newAPIKey"}"""))(id)
    this.copy(apiKey = Some(newAPIKey))
  }
}

object User {

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
    User(-1, null, OSMProfile(osmId.toLong,
      displayName,
      description,
      avatarURL,
      location,
      DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").parseDateTime(osmAccountCreated),
      requestToken
    ))
  }

  def apply(userXML:String, requestToken: RequestToken) : User =
    apply(XML.loadString(userXML), requestToken)

  val guestUser = User(0, DateTime.now(),
    OSMProfile(0, "Guest",
      "Sign in using your OSM account for more access to Map Roulette features.",
      "assets/images/user_no_image.png",
      Location(47.6097, 122.3331),
      DateTime.now(),
      RequestToken("", "")
    ), None, true
  )

  def userOrMocked(user:Option[User]) : User = {
    user match {
      case Some(u) => u
      case None => guestUser
    }
  }
}
