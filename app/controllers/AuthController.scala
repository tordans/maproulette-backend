/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package controllers

import com.google.inject.Inject
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import org.maproulette.Config
import org.maproulette.exception._
import org.maproulette.framework.model.{Grant, GrantTarget, User}
import org.maproulette.framework.psql.{Order, Query}
import org.maproulette.framework.service.UserService
import org.maproulette.models.dal.DALManager
import org.maproulette.session.SessionManager
import org.maproulette.permissions.Permission
import org.maproulette.utils.Crypto
import play.api.libs.json.{JsString, Json}
import play.api.libs.oauth.OAuthCalculator
import play.api.mvc._
import play.shaded.oauth.oauth.signpost.exception.OAuthNotAuthorizedException
import play.api.libs.ws.WSClient
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Promise
import scala.util.{Failure, Success}
import scala.concurrent.Future

import java.security.SecureRandom
import java.math.BigInteger

/**
  * All the authentication actions go in this class
  *
  * @author cuthbertm
  */
class AuthController @Inject() (
    components: ControllerComponents,
    sessionManager: SessionManager,
    userService: UserService,
    dalManager: DALManager,
    permission: Permission,
    wsClient: WSClient,
    crypto: Crypto,
    val config: Config
) extends AbstractController(components)
    with StatusMessages {

  val logger: Logger = LoggerFactory.getLogger(classOf[AuthController])
  import scala.concurrent.ExecutionContext.Implicits.global

  /**
    * An action to call to authenticate a user using OAuth 1.0a against the OAuth OSM Provider
    *
    * @return Redirects back to the index page containing a valid session
    */
  def authenticate(): Action[AnyContent] = Action.async { implicit request =>
    MPExceptionUtil.internalAsyncExceptionCatcher { () =>
      val p        = Promise[Result]()
      val redirect = request.getQueryString("redirect").getOrElse("")
      request.getQueryString("oauth_verifier") match {
        case Some(verifier) =>
          sessionManager.retrieveUser(verifier) onComplete {
            case Success(user) =>
              // We received the authorized tokens in the OAuth object - store it before we proceed
              p success this.withOSMSession(
                user,
                Redirect(redirect, SEE_OTHER).withHeaders(("Cache-Control", "no-cache"))
              )

              Future(storeAPIKeyInOSM(user))

            case Failure(e) => p failure e
          }
        case None =>
          sessionManager.retrieveRequestToken(
            proxyRedirect(routes.AuthController.authenticate) + s"?redirect=${getRedirectURL(request, redirect)}"
          ) match {
            case Right(t) =>
              // We received the unauthorized tokens in the OAuth object - store it before we proceed
              p success Redirect(sessionManager.redirectUrl(t.token))
                .withHeaders(("Cache-Control", "no-cache"))
                .withSession(
                  SessionManager.KEY_TOKEN     -> t.token,
                  SessionManager.KEY_SECRET    -> t.secret,
                  SessionManager.KEY_USER_TICK -> DateTime.now().getMillis.toString
                )
            case Left(e) => p failure e
          }
      }
      p.future
    }
  }

//  def authenticate2: Action[AnyContent] = Action { implicit request =>
//    val clientId = "cFogtzRCeVtKryYGonnmjXy6yHx2hUHDYmsWA1ZYtnE"
//    val clientSecret = "56JSEHqLK3bVq5C9Z48sI0w53SUJ9wFOU4QiAsDtzkw"
//    val authorizeEndpoint = "https://master.apis.dev.openstreetmap.org/oauth2/authorize"
//    val redirectUrl = "http://127.0.0.1:9000/api/v2/callback"
//
//    def generateRandomState(length: Int = LENGTH, chars: Seq[Char] = UNICODE_ASCII_CHARACTER_SET): String = {
//      val rand = new SecureRandom()
//      val state = new Array[Char](length)
//      for (i <- 0 until length) {
//        state(i) = chars(rand.nextInt(chars.length))
//      }
//      new String(state)
//    }
//
//    val oauthUrl = s"$authorizeEndpoint?client_id=$clientId&redirect_uri=$redirectUrl&response_type=code&scope=read%20write%20prefs"
//    Redirect(oauthUrl)
//  }

  def callback(code: String): Action[AnyContent] = Action.async { implicit request =>
    MPExceptionUtil.internalAsyncExceptionCatcher { () =>
      val tokenEndpoint = "https://master.apis.dev.openstreetmap.org/oauth2/token"
      val clientId = "RflKMjhJsuY5ktIYzuaVJ5JMOasjwRDtV0mq2OLPaxk"
      val clientSecret = "MtLDgWWn-KvdWCFbmVf54EVP2KRP_B8RWEzNbJwghys"

      val requestBody = Json.obj(
        "grant_type" -> "authorization_code",
        "code" -> code,
        "client_id" -> clientId,
        "client_secret" -> clientSecret,
        "redirect_uri" -> "http://127.0.0.1:3000"
      )

      val responseFuture = for {
        response <- wsClient.url(tokenEndpoint).post(requestBody)
        result <- response.status match {
          case OK =>
            // Handle a successful response
            val accessToken = (response.json \ "access_token").as[String]
            // Store the access token securely for future use
            // e.g., in a session or a secure database
            Future.successful(Ok(s"Access token obtained successfully: $accessToken"))
          case _ =>
            // Handle a non-successful response
            val errorMessage = (response.json \ "error_description").asOpt[String].getOrElse("Failed to obtain access token")
            Future.successful(InternalServerError(errorMessage))
        }
      } yield result

      responseFuture.recover {
        case ex: Exception =>
          // Handle any exceptions that may occur during the POST request
          // e.g., log the error, return an error response, etc.
          ex.printStackTrace()
          val errorMessage = s"Failed to obtain access token: ${ex.getMessage()}"
          InternalServerError(errorMessage)
      }

    }
  }

  def authenticate2(): Action[AnyContent] = Action.async { implicit request =>
    MPExceptionUtil.internalAsyncExceptionCatcher { () =>
      val LENGTH = 48
      val UNICODE_ASCII_CHARACTER_SET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toSeq

      // Generate a random state string of a given length using a given set of characters
      def generateRandomState(length: Int = LENGTH, chars: Seq[Char] = UNICODE_ASCII_CHARACTER_SET): String = {
        val rand = new SecureRandom()
        val state = new Array[Char](length)
        for (i <- 0 until length) {
          state(i) = chars(rand.nextInt(chars.length))
        }
        new String(state)
      }

      val state = generateRandomState()
      val clientId = "RflKMjhJsuY5ktIYzuaVJ5JMOasjwRDtV0mq2OLPaxk"
      val authorizeEndpoint = "https://master.apis.dev.openstreetmap.org/oauth2/authorize"

      val params = Map(
        "client_id" -> clientId,
        "response_type" -> "code",
        "redirect_uri" -> "http://127.0.0.1:3000",
        "scope" -> "read_prefs write_api",
        "state" -> state
      )

      val url = wsClient.url(authorizeEndpoint).withQueryStringParameters(params.toSeq: _*).uri.toString
      wsClient.close()

      val json = Json.obj(
        "auth_url" -> url,
        "state" -> state
      )
      Future(Ok(json))
    }
  }

  def withOSMSession(user: User, result: Result): Result = {
    result.withSession(
      SessionManager.KEY_TOKEN     -> user.osmProfile.requestToken.token,
      SessionManager.KEY_SECRET    -> user.osmProfile.requestToken.secret,
      SessionManager.KEY_USER_ID   -> user.id.toString,
      SessionManager.KEY_OSM_ID    -> user.osmProfile.id.toString,
      SessionManager.KEY_USER_TICK -> DateTime.now().getMillis.toString
    )
  }

  def getRedirectURL(implicit request: Request[AnyContent], redirect: String): String = {
    val referer    = request.headers.get(REFERER)
    val defaultURL = "/"
    if (StringUtils.isEmpty(redirect) && referer.isDefined) {
      referer.get
    } else if (StringUtils.isNotEmpty(redirect)) {
      redirect
    } else {
      defaultURL
    }
  }

  private def proxyRedirect(call: Call)(implicit request: Request[AnyContent]): String = {
    config.proxyPort match {
      case Some(port) =>
        val applicationPort = System.getProperty("http.port")
        call
          .absoluteURL(config.isProxySSL)
          .replaceFirst(s":$applicationPort", s"${if (port == 80) {
            ""
          } else {
            s":$port"
          }}")
      case None => call.absoluteURL(config.isProxySSL)
    }
  }

  def signIn(redirect: String): Action[AnyContent] = Action.async { implicit request =>
    MPExceptionUtil.internalAsyncExceptionCatcher { () =>
      val p = Promise[Result]()
      request.body.asFormUrlEncoded match {
        case Some(data) =>
          val username = data.getOrElse("signInUsername", ArrayBuffer("")).mkString
          val apiKey   = data.getOrElse("signInAPIKey", ArrayBuffer("")).mkString
          this.sessionManager.retrieveUser(username, apiKey) match {
            case Some(user) =>
              p success this.withOSMSession(
                user,
                Redirect(getRedirectURL(request, redirect))
                  .withHeaders(("Cache-Control", "no-chache"))
              )
            case None =>
              p failure new OAuthNotAuthorizedException("Invalid username or apiKey provided")
          }
        case None =>
          p failure new OAuthNotAuthorizedException("Invalid username or apiKey provided")
      }
      p.future
    }
  }

  /**
    * Signs out the user, creating essentially a blank new session and responds with a 200 OK
    *
    * @return 200 OK Status
    */
  def signOut(): Action[AnyContent] = Action { implicit request =>
    Ok.withNewSession
  }

  def deleteUser(userId: Long): Action[AnyContent] = Action.async { implicit request =>
    implicit val requireSuperUser: Boolean = true
    sessionManager.authenticatedRequest { implicit user =>
      Ok(
        Json.toJson(
          StatusMessage(
            "OK",
            JsString(
              s"${this.userService.delete(userId, user)} User deleted by super user ${user.name} [${user.id}]."
            )
          )
        )
      )
    }
  }

  private def storeAPIKeyInOSM: User => Unit = (user: User) => {
    if (config.getOSMServer.nonEmpty && config.getOSMPreferences.nonEmpty) {
      logger.debug("Attempting to save api key for userId={} to their OSM preferences", user.id)
      val decryptedAPIKey = User.withDecryptedAPIKey(user)(crypto).apiKey.getOrElse("")

      wsClient
        .url(s"${config.getOSMServer}${config.getOSMPreferences}")
        .withHttpHeaders(ACCEPT -> JSON)
        .sign(OAuthCalculator(config.getOSMOauth.consumerKey, user.osmProfile.requestToken))
        .put(decryptedAPIKey) onComplete {
        case Success(response) =>
          if (response.status != 200) {
            logger.info(
              "API key unsuccessfully stored in OSM preferences for user id {}. Status code {}",
              user.id,
              response.status
            )
          } else {
            logger.debug(
              "API key stored in OSM preferences for user id {}. Status code {}",
              user.id,
              response.status
            )
          }
        case Failure(e) =>
          logger.info("Future failed to store OSM preference for userId={}", user.id, e)
      }
    } else {
      logger.debug("Conf lacks required settings to store maproulette users' API key in OSM")
    }
  }

  /**
    * Generates a new API key for the user. A user can then use the API key to make API calls directly against
    * the server. Only the current API key for the user will work on any authenticated API calls, any previous
    * keys are immediately discarded once a new one is created.
    *
    * @return Will return NoContent if cannot create the key (which most likely means that no user was
    *         found, or will return the api key as plain text.
    */
  def generateAPIKey(userId: Long = -1): Action[AnyContent] = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      val newAPIUser = if (permission.isSuperUser(user) && userId != -1) {
        this.userService.retrieve(userId) match {
          case Some(u) => u
          case None =>
            throw new NotFoundException(
              s"No user found with id [$userId], no API key could be generated."
            )
        }
      } else {
        user
      }
      this.userService.generateAPIKey(newAPIUser, user) match {
        case Some(updated) =>
          updated.apiKey match {
            case Some(api) => {
              Future(storeAPIKeyInOSM(user))
              Ok(api)
            }
            case None => NoContent
          }
        case None => NoContent
      }
    }
  }

  /**
    * Super user action that will reset all the api keys.
    *
    * @return Simple Ok if succeeded.
    */
  def resetAllAPIKeys(): Action[AnyContent] = Action.async { implicit request =>
    implicit val requireSuperUser: Boolean = true
    sessionManager.authenticatedRequest { implicit user =>
      this.userService
        .query(Query.simple(List.empty, order = Order > User.FIELD_ID), user)
        .foreach { apiUser =>
          this.userService.generateAPIKey(apiUser, user)
        }
      Ok
    }
  }

  /**
    * Adds an Admin role on the project to the user
    *
    * @param projectId The id of the project to add the user too
    * @return NoContent
    */
  def addUserToProject(userId: Long, projectId: Long): Action[AnyContent] = Action.async {
    implicit request =>
      implicit val requireSuperUser: Boolean = true
      sessionManager.authenticatedRequest { implicit user =>
        this.userService.retrieve(userId) match {
          case Some(addUser) =>
            val projectTarget = GrantTarget.project(projectId)
            if (addUser.grants
                  .exists(g => g.target == projectTarget && g.role == Grant.ROLE_ADMIN)) {
              throw new InvalidException(
                s"User ${addUser.name} is already an admin of project $projectId"
              )
            }
            this.userService
              .addUserToProject(addUser.osmProfile.id, projectId, Grant.ROLE_ADMIN, user)
            Ok(
              Json.toJson(
                StatusMessage(
                  "OK",
                  JsString(s"User ${addUser.name} made admin of project $projectId")
                )
              )
            )
          case None => throw new NotFoundException(s"Could not find user with ID $userId")
        }
      }
  }
}
