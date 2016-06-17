package org.maproulette.session.dal

import java.sql.Connection
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

import anorm._
import anorm.SqlParser._
import com.vividsolutions.jts.geom.{Coordinate, GeometryFactory, Point}
import com.vividsolutions.jts.io.{WKTReader, WKTWriter}
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import org.maproulette.actions.UserType
import org.maproulette.models.dal.{BaseDAL, ProjectDAL}
import org.maproulette.session.{Group, Location, OSMProfile, User}
import play.api.db.Database
import org.maproulette.cache.CacheManager
import org.maproulette.exception.NotFoundException
import org.maproulette.models.Project
import org.maproulette.permissions.Permission
import play.api.libs.Crypto
import play.api.libs.json.{JsValue, Json}
import play.api.libs.oauth.RequestToken

/**
  * The data access layer for the user object. This is considered a special object in the system,
  * as it does not use the baseObject for the user class and does not rely on the BaseDAL like all
  * the other objects. This is somewhat related to how the id's for the User are generated and used.
  *
  * TODO: This object should be locked down more than it currently is. Currently althoguh you cannot
  * write to any of the objects without super user access, you can list all the users, which is
  * definitely not desirable, so will need to block any listing access unless you are a super user.
  *
  * @author cuthbertm
  */
@Singleton
class UserDAL @Inject() (override val db:Database,
                         userGroupDAL: UserGroupDAL,
                         projectDAL:ProjectDAL,
                         override val permission:Permission) extends BaseDAL[Long, User] {

  import org.maproulette.utils.AnormExtension._

  // The cache manager for the users
  override val cacheManager = new CacheManager[Long, User]
  override val tableName = "users"
  override val retrieveColumns: String = "*, ST_AsText(users.home_location) AS home"

  // The anorm row parser to convert user records from the database to user objects
  val parser: RowParser[User] = {
    get[Long]("users.id") ~
      get[Long]("users.osm_id") ~
      get[DateTime]("users.created") ~
      get[DateTime]("users.modified") ~
      get[String]("users.theme") ~
      get[DateTime]("users.osm_created") ~
      get[String]("users.name") ~
      get[Option[String]]("users.description") ~
      get[Option[String]]("users.avatar_url") ~
      get[Option[String]]("home") ~
      get[Option[String]]("users.api_key") ~
      get[String]("users.oauth_token") ~
      get[String]("users.oauth_secret") map {
      case id ~ osmId ~ created ~ modified ~ theme ~ osmCreated ~ displayName ~ description ~
        avatarURL ~ homeLocation ~ apiKey ~ oauthToken ~ oauthSecret =>
        val locationWKT = homeLocation match {
          case Some(wkt) => new WKTReader().read(wkt).asInstanceOf[Point]
          case None => new GeometryFactory().createPoint(new Coordinate(0, 0))
        }
        // If the modified date is too old, then lets update this user information from OSM
        new User(id, created, modified, theme,
          OSMProfile(osmId, displayName, description.getOrElse(""), avatarURL.getOrElse(""),
            Location(locationWKT.getX, locationWKT.getY), osmCreated, RequestToken(oauthToken, oauthSecret)),
            userGroupDAL.getGroups(osmId, User.superUser), apiKey)
    }
  }

  /**
    * Find the user based on the user's osm ID. If found on cache, will return cached object
    * instead of hitting the database
    *
    * @param id The user's osm ID
    * @return The matched user, None if User not found
    */
  def retrieveByOSMID(implicit id: Long, user:User): Option[User] = cacheManager.withOptionCaching { () =>
    db.withConnection { implicit c =>
      val query = s"""SELECT $retrieveColumns FROM users WHERE osm_id = {id}"""
      SQL(query).on('id -> id).as(parser.*).headOption match {
        case Some(u) =>
          permission.hasReadAccess(u, user)
          Some(u)
        case None => None
      }
    }
  }

  /**
    * Find the User based on an API key, the API key is unique in the database.
    *
    * @param apiKey The APIKey to match against
    * @param id The id of the user
    * @return The matched user, None if User not found
    */
  def retrieveByAPIKey(apiKey:String, user:User)(implicit id:Long) : Option[User] = cacheManager.withOptionCaching { () =>
    db.withConnection { implicit c =>
      val query = s"""SELECT $retrieveColumns FROM users WHERE id = {id} AND api_key = {apiKey}"""
      SQL(query).on('id -> id, 'apiKey -> apiKey).as(parser.*).headOption match {
        case Some(u) =>
          permission.hasReadAccess(u, user)
          Some(u)
        case None => None
      }
    }
  }

  /**
    * Match the user based on the token, secret and id for the user.
    *
    * @param id The id of the user
    * @param requestToken The request token containing the access token and secret
    * @return The matched user, None if User not found
    */
  def matchByRequestTokenAndId(requestToken: RequestToken, user:User)(implicit id:Long): Option[User] = {
    val requestedUser = cacheManager.withCaching { () =>
      db.withConnection { implicit c =>
        val query = s"""SELECT $retrieveColumns FROM users
                        WHERE id = {id} AND oauth_token = {token} AND oauth_secret = {secret}"""
        SQL(query).on('id -> id, 'token -> requestToken.token, 'secret -> requestToken.secret).as(parser.*).headOption
      }
    }
    requestedUser match {
      case Some(u) =>
        // double check that the token and secret still match, in case it came from the cache
        if (StringUtils.equals(u.osmProfile.requestToken.token, requestToken.token) &&
          StringUtils.equals(u.osmProfile.requestToken.secret, requestToken.secret)) {
          permission.hasReadAccess(u, user)
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
  def matchByRequestToken(requestToken: RequestToken, user:User): Option[User] = {
    db.withConnection { implicit c =>
      val query = s"""SELECT $retrieveColumns FROM users
                      WHERE oauth_token = {token} AND oauth_secret = {secret}"""
      SQL(query).on('token -> requestToken.token, 'secret -> requestToken.secret).as(parser.*).headOption
    }
  }

  /**
    * "Upsert" function that will insert a new user into the database, if the user already exists in
    * the database it will simply update the user with new information. A user is considered to exist
    * in the database if the id or osm_id is found in the users table. During an insert any groups
    * for the user will be ignored, to update groups or add/remove groups, the specific functions
    * for that must be used. Or the update method which has a specific mechanism for updating
    * groups
    *
    * @param user The user to update
    * @return None if failed to update or create.
    */
  override def insert(item:User, user: User)(implicit c:Connection=null): User = cacheManager.withOptionCaching { () =>
    permission.hasWriteAccess(item, user)
    withMRTransaction { implicit c =>
      val ewkt = new WKTWriter().write(
        new GeometryFactory().createPoint(
          new Coordinate(item.osmProfile.homeLocation.latitude, item.osmProfile.homeLocation.longitude)
        )
      )

      val query = s"""WITH upsert AS (UPDATE users SET osm_id = {osmID}, osm_created = {osmCreated},
                              name = {name}, description = {description}, avatar_url = {avatarURL},
                              oauth_token = {token}, oauth_secret = {secret}, theme = {theme},
                              home_location = ST_GeomFromEWKT({wkt})
                            WHERE id = {id} OR osm_id = {osmID} RETURNING $retrieveColumns)
            INSERT INTO users (osm_id, osm_created, name, description,
                               avatar_url, oauth_token, oauth_secret, theme, home_location)
            SELECT {osmID}, {osmCreated}, {name}, {description}, {avatarURL}, {token}, {secret},
                    {theme}, ST_GeomFromEWKT({wkt})
            WHERE NOT EXISTS (SELECT * FROM upsert)"""
      SQL(query).on(
        'osmID -> item.osmProfile.id,
        'osmCreated -> item.osmProfile.created,
        'name -> item.osmProfile.displayName,
        'description -> item.osmProfile.description,
        'avatarURL -> item.osmProfile.avatarURL,
        'token -> item.osmProfile.requestToken.token,
        'secret -> item.osmProfile.requestToken.secret,
        'theme -> item.theme,
        'wkt -> s"SRID=4326;$ewkt",
        'id -> item.id
      ).executeUpdate()
    }
    // We do this separately from the transaction because if we don't the user_group mappings
    // wont be accessible just yet.
    val retUser = db.withConnection { implicit c =>
      val query = s"""SELECT $retrieveColumns FROM users WHERE osm_id = {id}"""
      SQL(query).on('id -> item.osmProfile.id).as(parser.*).head
    }

    // now update the groups by adding any new groups, from the supplied user
    val nuGroups = db.withTransaction { implicit c =>
      val newGroups = item.groups.filter(g => !retUser.groups.exists(_.id == g.id))
      newGroups.foreach(g => addUserToGroup(item.osmProfile.id, g, User.superUser))
      retUser.groups ++ newGroups
    }
    Some(retUser.copy(groups = nuGroups))
  }.get

  /**
    * Only certain values are allowed to be updated for the user. Namely apiKey, displayName,
    * description, avatarURL, token, secret and theme.
    *
    * @param value The json object containing the fields to update
    * @param id The id of the user to update
    * @return The user that was updated, None if no user was found with the id
    */
  override def update(value:JsValue, user:User)(implicit id:Long, c:Connection=null): Option[User] = {
    cacheManager.withUpdatingCache(Long => retrieveById) { implicit cachedItem =>
      permission.hasWriteAccess(cachedItem, user)
      withMRTransaction { implicit c =>
        val apiKey = (value \ "apiKey").asOpt[String].getOrElse(cachedItem.apiKey.getOrElse(""))
        val displayName = (value \ "osmProfile" \ "displayName").asOpt[String].getOrElse(cachedItem.osmProfile.displayName)
        val description = (value \ "osmProfile" \ "description").asOpt[String].getOrElse(cachedItem.osmProfile.description)
        val avatarURL = (value \ "osmProfile" \ "avatarURL").asOpt[String].getOrElse(cachedItem.osmProfile.avatarURL)
        val token = (value \ "osmProfile" \ "token").asOpt[String].getOrElse(cachedItem.osmProfile.requestToken.token)
        val secret = (value \ "osmProfile" \ "secret").asOpt[String].getOrElse(cachedItem.osmProfile.requestToken.secret)
        val theme = (value \ "theme").asOpt[String].getOrElse(cachedItem.theme)
        // todo: allow to insert in WKT, WKB or latitude/longitude
        val latitude = (value \ "osmProfile" \ "homeLocation" \ "latitude").asOpt[Double].getOrElse(cachedItem.osmProfile.homeLocation.latitude)
        val longitude = (value \ "osmProfile" \ "homeLocation" \ "longitude").asOpt[Double].getOrElse(cachedItem.osmProfile.homeLocation.longitude)
        val ewkt = new WKTWriter().write(new GeometryFactory().createPoint(new Coordinate(latitude, longitude)))

        updateGroups(value, user)

        val query = s"""UPDATE users SET api_key = {apiKey}, name = {name}, description = {description},
                                          avatar_url = {avatarURL}, oauth_token = {token}, oauth_secret = {secret},
                                          theme = {theme}, home_location = ST_GeomFromEWKT({wkt})
                        WHERE id = {id} RETURNING $retrieveColumns"""
        SQL(query).on(
          'apiKey -> apiKey,
          'name -> displayName,
          'description -> description,
          'avatarURL -> avatarURL,
          'token -> token,
          'secret -> secret,
          'theme -> theme,
          'wkt -> s"SRID=4326;$ewkt",
          'id -> id
        ).as(parser.*).headOption
      }
    }
  }

  def updateGroups(value:JsValue, user:User)(implicit id:Long, c:Connection=null): Unit = {
    permission.hasSuperAccess(user)
    withMRTransaction { implicit c =>
      // list of grousp to delete
      (value \ "groups" \ "delete").asOpt[List[Long]] match {
        case Some(values) => SQL"""DELETE FROM user_groups WHERE group_id IN ($values)""".execute()
        case None => //ignore
      }
      (value \ "groups" \ "add").asOpt[List[Long]] match {
        case Some(values) =>
          val sqlQuery = s"""INSERT INTO user_groups (user_id, group_id) VALUES ($id, {groupId})"""
          val parameters = values.map(groupId => {
            Seq[NamedParameter]("groupId" -> groupId)
          })
          BatchSql(sqlQuery, parameters.head, parameters.tail:_*).execute()
        case None => //ignore
      }
    }
  }

  /**
    * Deletes a user from the database based on a specific user id
    *
    * @param id The user to delete
    * @return The rows that were deleted
    */
  override def delete(id: Long, user:User)(implicit c:Connection=null) : User = {
    permission.hasSuperAccess(user)
    super.delete(id, user)
  }

  /**
    * Delete a user based on their OSM ID
    *
    * @param osmId The OSM ID for the user
    * @param user The user deleting the user
    * @return
    */
  def deleteByOsmID(osmId:Long, user:User)(implicit c:Connection=null) : Int = {
    permission.hasSuperAccess(user)
    implicit val ids = List(osmId)
    cacheManager.withCacheIDDeletion { () =>
      withMRTransaction { implicit c =>
        SQL"""DELETE FROM users WHERE osm_id = $osmId""".executeUpdate()
      }
    }
  }

  /**
    * Adds a user to a project
    *
    * @param osmID The OSM ID of the user to add to the project
    * @param projectId The project that user is being added too
    * @param user The user that is adding the user to the project
    */
  def addUserToProject(osmID:Long, projectId:Long, user:User)(implicit c:Connection=null) : User = {
    permission.hasSuperAccess(user)
    implicit val osmKey = osmID
    implicit val superUser = user
    cacheManager.withUpdatingCache(Long => retrieveByOSMID) { cachedUser =>
      withMRTransaction { implicit c =>
        SQL"""INSERT INTO user_groups (osm_user_id, group_id)
            SELECT $osmID, id FROM groups
            WHERE group_type = 1 AND project_id = $projectId
         """.executeUpdate()
      }
      Some(cachedUser.copy(groups = userGroupDAL.getGroups(osmID, superUser)))
    }.get
  }

  /**
    * Add a user to a group
    *
    * @param osmID The OSM ID of the user to add to the project
    * @param group The group that user is being added too
    * @param user The user that is adding the user to the project
    */
  def addUserToGroup(osmID:Long, group:Group, user:User)(implicit c:Connection=null) : Unit = {
    permission.hasSuperAccess(user)
    withMRTransaction { implicit c =>
      SQL"""INSERT INTO user_groups (osm_user_id, group_id) VALUES ($osmID, ${group.id})""".executeUpdate()
    }
  }

  /**
    * Removes a user from a group
    *
    * @param osmID The OSM ID of the user
    * @param group The group that you are removing from the user
    * @param user The user executing the request
    * @param c An implicit connection if applicable
    */
  def removeUserFromGroup(osmID:Long, group:Group, user:User)(implicit c:Connection=null) : Unit = {
    permission.hasSuperAccess(user)
    withMRTransaction { implicit c =>
      SQL"""DELETE FROM user_groups WHERE osm_user_id = $osmID AND group_id = ${group.id}""".executeUpdate()
    }
  }

  /**
    * Generates a new API key for the user
    *
    * @param userId The user that is requesting that their key be updated.
    * @return An optional variable that will contain the updated user if successful
    */
  def generateAPIKey(userId:Long, user:User) : Option[User] = {
    permission.hasWriteAccess(UserType(), user)(userId)
    val newAPIKey = Crypto.encryptAES(userId + "|" + UUID.randomUUID())
    this.update(Json.parse(s"""{"apiKey":"$newAPIKey"}"""), User.superUser)(userId)
  }

  /**
    * Initializes the home project for the user. If the project already exists, then we are
    * good.
    *
    * @param user
    */
  def initializeHomeProject(user:User) : User = {
    val homeName = s"Home_${user.osmProfile.id}"
    val homeProjectId = projectDAL.retrieveByName(homeName) match {
      case Some(project) => project.id
      case None =>
        projectDAL.insert(Project(id = -1,
          name = homeName,
          description = Some(s"Home project for user ${user.name}"),
          enabled = false
        ), User.superUser).id
    }
    // make sure the user is an admin of this project
    if (!user.groups.exists(g => g.projectId == homeProjectId)) {
      addUserToProject(user.osmProfile.id, homeProjectId, User.superUser)
    } else {
      user
    }
  }

  /**
    * Retrieves the user's home project
    *
    * @param user
    * @return
    */
  def getHomeProject(user:User) : Project = {
    val homeName = s"Home_${user.osmProfile.id}"
    projectDAL.retrieveByName(homeName) match {
      case Some(project) => project
      case None => throw new NotFoundException("You should never get this exception, Home project should always exist for user.")
    }
  }
}
