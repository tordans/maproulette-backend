package org.maproulette.session.dal

import anorm._
import anorm.SqlParser._
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import org.maproulette.session.{Location, OSMProfile, User}
import play.api.db.DB
import play.api.Play.current
import org.maproulette.cache.CacheManager
import play.api.libs.json.JsValue
import play.api.libs.oauth.RequestToken

/**
  * @author cuthbertm
  */
object UserDAL {

  import org.maproulette.utils.AnormExtension._

  val cacheManager = new CacheManager[Long, User]

  val parser: RowParser[User] = {
    get[Long]("users.id") ~
      get[Long]("users.osm_id") ~
      get[DateTime]("users.created") ~
      get[DateTime]("users.osm_created") ~
      get[String]("users.display_name") ~
      get[Option[String]]("users.description") ~
      get[Option[String]]("users.avatar_url") ~
      get[Option[String]]("users.api_key") ~
      get[String]("users.oauth_token") ~
      get[String]("users.oauth_secret") map {
      case id ~ osmId ~ created ~ osmCreated ~ displayName ~ description ~ avatarURL ~ apiKey ~ oauthToken ~ oauthSecret =>
        // If the modified date is too old, then lets update this user information from OSM
        new User(id, created,
          OSMProfile(osmId, displayName, description.getOrElse(""), avatarURL.getOrElse(""),
            Location(0, 0), osmCreated, RequestToken(oauthToken, oauthSecret)), apiKey)
    }
  }

  def findByID(implicit id: Long): Option[User] = cacheManager.withOptionCaching { () =>
    DB.withConnection { implicit c =>
      SQL"""SELECT * FROM users WHERE id = $id""".as(parser.*).headOption
    }
  }

  def findByOSMID(implicit id: Long): Option[User] = cacheManager.withOptionCaching { () =>
    DB.withConnection { implicit c =>
      SQL"""SELECT * FROM users WHERE osm_id = $id""".as(parser.*).headOption
    }
  }

  def matchByRequestTokenAndId(id: Long, requestToken: RequestToken): Option[User] = {
    implicit val userId = id
    val user = cacheManager.withOptionCaching { () =>
      DB.withConnection { implicit c =>
        SQL"""SELECT * FROM users WHERE id = $id AND oauth_token = ${requestToken.token}
             AND oauth_secret = ${requestToken.secret}""".as(parser.*).headOption
      }
    }
    user match {
      case Some(u) =>
        // double check that the token and secret still match, in case it came from the cache
        if (StringUtils.equals(u.osmProfile.requestToken.token, requestToken.token) &&
          StringUtils.equals(u.osmProfile.requestToken.secret, requestToken.secret)) {
          Some(u)
        } else {
          None
        }
      case None => None
    }
  }

  def matchByRequestToken(requestToken: RequestToken): Option[User] = {
    DB.withConnection { implicit c =>
      SQL"""SELECT * FROM users WHERE oauth_token = ${requestToken.token}
           AND oauth_secret = ${requestToken.secret}""".as(parser.*).headOption
    }
  }

  def create(user: User): Option[User] = cacheManager.withOptionCaching { () =>
    DB.withConnection { implicit c =>
      SQL"""INSERT INTO users (osm_id, osm_created, display_name, description,
                          avatar_url, api_key, oauth_token, oauth_secret)
            VALUES (${user.osmProfile.id}, ${user.osmProfile.created}, ${user.osmProfile.displayName},
                    ${user.osmProfile.description}, ${user.osmProfile.avatarURL}, ${user.apiKey},
                    ${user.osmProfile.requestToken.token}, ${user.osmProfile.requestToken.secret})
            RETURNING *""".as(parser.*).headOption
    }
  }

  /**
    * Only certain values are allowed to be updated for the user.
    *
    * @param value
    * @param id
    * @return
    */
  def update(value:JsValue)(implicit id:Long): Option[User] = {
    cacheManager.withUpdatingCache(Long => findByID) { implicit cachedItem =>
      DB.withTransaction { implicit c =>
        val apiKey = (value \ "apiKey").asOpt[String].getOrElse(cachedItem.apiKey.getOrElse(""))
        val displayName = (value \ "osmProfile" \ "displayName").asOpt[String].getOrElse(cachedItem.osmProfile.displayName)
        val description = (value \ "osmProfile" \ "description").asOpt[String].getOrElse(cachedItem.osmProfile.description)
        val avatarURL = (value \ "osmProfile" \ "avatarURL").asOpt[String].getOrElse(cachedItem.osmProfile.avatarURL)
        val token = (value \ "osmProfile" \ "token").asOpt[String].getOrElse(cachedItem.osmProfile.requestToken.token)
        val secret = (value \ "osmProfile" \ "secret").asOpt[String].getOrElse(cachedItem.osmProfile.requestToken.secret)

        SQL"""UPDATE users SET api_key = $apiKey, display_name = $displayName, description = $description,
                avatar_url = $avatarURL, token = $token, secret = $secret
              WHERE id = $id RETURNING *""".as(parser.*).headOption
      }
    }
  }

  def delete(implicit id: Long) = {
    implicit val ids = List(id)
    cacheManager.withCacheIDDeletion { () =>
      DB.withConnection { implicit c =>
        SQL"""DELETE FROM users WHERE id = $id"""
      }
    }
  }
}
