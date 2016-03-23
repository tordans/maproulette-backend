package org.maproulette.session.dal

import javax.inject.Inject
import javax.inject.Singleton

import anorm._
import anorm.SqlParser._
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import org.maproulette.models.dal.BaseDAL
import org.maproulette.session.{Group, Location, OSMProfile, User}
import play.api.db.Database
import org.maproulette.cache.CacheManager
import play.api.libs.json.JsValue
import play.api.libs.oauth.RequestToken

/**
  * The data access layer for the user object. This is considered a special object in the system,
  * as it does not use the baseObject for the user class and does not rely on the BaseDAL like all
  * the other objects. This is somewhat related to how the id's for the User are generated and used.
  *
  * @author cuthbertm
  */
@Singleton
class UserDAL @Inject() (override val db:Database, userGroupDAL: UserGroupDAL) extends BaseDAL[Long, User] {

  import org.maproulette.utils.AnormExtension._

  // The cache manager for the users
  override val cacheManager = new CacheManager[Long, User]
  override val tableName = "users"

  // The anorm row parser to convert user records from the database to user objects
  val parser: RowParser[User] = {
    get[Long]("users.id") ~
      get[Long]("users.osm_id") ~
      get[DateTime]("users.created") ~
      get[DateTime]("users.modified") ~
      get[String]("users.theme") ~
      get[DateTime]("users.osm_created") ~
      get[String]("users.display_name") ~
      get[Option[String]]("users.description") ~
      get[Option[String]]("users.avatar_url") ~
      get[Option[String]]("users.api_key") ~
      get[String]("users.oauth_token") ~
      get[String]("users.oauth_secret") map {
      case id ~ osmId ~ created ~ modified ~ theme ~ osmCreated ~ displayName ~ description ~
        avatarURL ~ apiKey ~ oauthToken ~ oauthSecret =>
        // If the modified date is too old, then lets update this user information from OSM
        new User(id, created, modified, theme,
          OSMProfile(osmId, displayName, description.getOrElse(""), avatarURL.getOrElse(""),
            Location(0, 0), osmCreated, RequestToken(oauthToken, oauthSecret)),
            userGroupDAL.getGroups(id), apiKey)
    }
  }

  /**
    * Find the user based on the user's osm ID. If found on cache, will return cached object
    * instead of hitting the database
    *
    * @param id The user's osm ID
    * @return The matched user, None if User not found
    */
  def retrieveByOSMID(implicit id: Long): Option[User] = cacheManager.withOptionCaching { () =>
    db.withConnection { implicit c =>
      SQL"""SELECT * FROM users WHERE osm_id = $id""".as(parser.*).headOption
    }
  }

  /**
    * Find the User based on an API key, the API key is unique in the database.
    *
    * @param apiKey The APIKey to match against
    * @param id The id of the user
    * @return The matched user, None if User not found
    */
  def retrieveByAPIKey(apiKey:String)(implicit id:Long) : Option[User] = cacheManager.withOptionCaching { () =>
    db.withConnection { implicit c =>
      SQL"""SELECT * FROM users WHERE id = $id AND api_key = ${apiKey}""".as(parser.*).headOption
    }
  }

  /**
    * Match the user based on the token, secret and id for the user.
    *
    * @param id The id of the user
    * @param requestToken The request token containing the access token and secret
    * @return The matched user, None if User not found
    */
  def matchByRequestTokenAndId(requestToken: RequestToken)(implicit id:Long): Option[User] = {
    val user = cacheManager.withCaching { () =>
      db.withConnection { implicit c =>
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

  /**
    * Match the user based on the token and secret for the user.
    *
    * @param requestToken The request token containing the access token and secret
    * @return The matched user, None if User not found
    */
  def matchByRequestToken(requestToken: RequestToken): Option[User] = {
    db.withConnection { implicit c =>
      SQL"""SELECT * FROM users WHERE oauth_token = ${requestToken.token}
           AND oauth_secret = ${requestToken.secret}""".as(parser.*).headOption
    }
  }

  /**
    * "Upsert" function that will insert a new user into the database, if the user already exists in
    * the database it will simply update the user with new information. A user is considered to exist
    * in the database if the id or osm_id is found in the users table.
    *
    * @param user The user to update
    * @return None if failed to update or create.
    */
  override def insert(item:User, user: User): User = cacheManager.withOptionCaching { () =>
    user.hasWriteAccess(user)
    db.withTransaction { implicit c =>
      SQL"""WITH upsert AS (UPDATE users SET osm_id = ${item.osmProfile.id}, osm_created = ${item.osmProfile.created},
                              display_name = ${item.osmProfile.displayName}, description = ${item.osmProfile.description},
                              avatar_url = ${item.osmProfile.avatarURL},
                              oauth_token = ${item.osmProfile.requestToken.token},
                              oauth_secret = ${item.osmProfile.requestToken.secret},
                              theme = ${item.theme}
                            WHERE id = ${item.id} OR osm_id = ${item.osmProfile.id} RETURNING *)
            INSERT INTO users (osm_id, osm_created, display_name, description,
                               avatar_url, oauth_token, oauth_secret, theme)
            SELECT ${item.osmProfile.id}, ${item.osmProfile.created}, ${item.osmProfile.displayName},
                    ${item.osmProfile.description}, ${item.osmProfile.avatarURL},
                    ${item.osmProfile.requestToken.token}, ${item.osmProfile.requestToken.secret},
                    ${item.theme}
            WHERE NOT EXISTS (SELECT * FROM upsert)""".executeUpdate()
      SQL"""SELECT * FROM users WHERE osm_id = ${item.osmProfile.id}""".as(parser.*).headOption
    }
  }.get

  /**
    * Only certain values are allowed to be updated for the user. Namely apiKey, displayName,
    * description, avatarURL, token, secret and theme.
    *
    * @param value The json object containing the fields to update
    * @param id The id of the user to update
    * @return The user that was updated, None if no user was found with the id
    */
  override def update(value:JsValue, user:User)(implicit id:Long): Option[User] = {
    cacheManager.withUpdatingCache(Long => retrieveById) { implicit cachedItem =>
      cachedItem.hasWriteAccess(user)
      db.withTransaction { implicit c =>
        val apiKey = (value \ "apiKey").asOpt[String].getOrElse(cachedItem.apiKey.getOrElse(""))
        val displayName = (value \ "osmProfile" \ "displayName").asOpt[String].getOrElse(cachedItem.osmProfile.displayName)
        val description = (value \ "osmProfile" \ "description").asOpt[String].getOrElse(cachedItem.osmProfile.description)
        val avatarURL = (value \ "osmProfile" \ "avatarURL").asOpt[String].getOrElse(cachedItem.osmProfile.avatarURL)
        val token = (value \ "osmProfile" \ "token").asOpt[String].getOrElse(cachedItem.osmProfile.requestToken.token)
        val secret = (value \ "osmProfile" \ "secret").asOpt[String].getOrElse(cachedItem.osmProfile.requestToken.secret)
        val theme = (value \ "theme").asOpt[String].getOrElse(cachedItem.theme)

        SQL"""UPDATE users SET api_key = $apiKey, display_name = $displayName, description = $description,
                avatar_url = $avatarURL, oauth_token = $token, oauth_secret = $secret
              WHERE id = $id RETURNING *""".as(parser.*).headOption
      }
    }
  }

  /**
    * Deletes a user from the database based on a specific user id
    *
    * @param id The user to delete
    * @return The rows that were deleted
    */
  def delete(implicit id: Long) : Int = {
    implicit val ids = List(id)
    cacheManager.withCacheIDDeletion { () =>
      db.withConnection { implicit c =>
        SQL"""DELETE FROM users WHERE id = $id""".executeUpdate()
      }
    }
  }

  def deleteByOsmID(implicit osmId:Long) : Int = {
    implicit val ids = List(osmId)
    cacheManager.withCacheIDDeletion { () =>
      db.withConnection { implicit c =>
        SQL"""DELETE FROM users WHERE osm_id = $osmId""".executeUpdate()
      }
    }
  }

  def addUserToGroup(user:User, group:Group) : User = {
    db.withConnection { implicit c =>
      SQL"""INSERT INTO user_groups (user_id, group_id) VALUES (${user.id}, ${group.id})""".executeUpdate()
      user.copy(groups = user.groups ++ List(group))
    }
  }
}
