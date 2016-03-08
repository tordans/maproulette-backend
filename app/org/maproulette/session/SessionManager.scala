package org.maproulette.session

import dal.UserDAL
import io.netty.handler.codec.http.HttpResponseStatus
import oauth.signpost.exception.OAuthNotAuthorizedException
import play.api.Logger
import play.api.Play.current
import play.api.libs.Crypto
import play.api.libs.oauth._
import play.api.libs.ws.WS
import play.api.mvc.{Result, Request, AnyContent, RequestHeader}

import scala.concurrent.{Promise, Future}
import scala.util.{Success, Failure}

/**
  * @author cuthbertm
  */
object SessionManager {
  import scala.concurrent.ExecutionContext.Implicits.global

  private val userDetailsURL = current.configuration.getString("osm.userDetails").get
  private val requestTokenURL = current.configuration.getString("osm.requestTokenURL").get
  private val accessTokenURL = current.configuration.getString("osm.accessTokenURL").get
  private val authorizationURL = current.configuration.getString("osm.authorizationURL").get
  private val consumerKey = ConsumerKey(current.configuration.getString("osm.consumerKey").get,
    current.configuration.getString("osm.consumerSecret").get)

  private val oauth = OAuth(ServiceInfo(requestTokenURL, accessTokenURL, authorizationURL, consumerKey), true)

  def retrieveUser(verifier:String)(implicit request:Request[AnyContent]) : Future[User] = {
    val p = Promise[User]
    sessionTokenPair match {
      case Some(pair) =>
        oauth.retrieveAccessToken(pair, verifier) match {
          case Right(accessToken) =>
            sessionUser(Some(accessToken), true)(request) onComplete {
              case Success(user) =>
                user match {
                  case Some(u) => p success u.copy(osmProfile = u.osmProfile.copy(requestToken = accessToken))
                  case None => p failure new OAuthNotAuthorizedException()
                }

              case Failure(e) =>
                Logger.error(e.getMessage, e)
                p failure new OAuthNotAuthorizedException()
            }
          case Left(e) =>
            Logger.error(e.getMessage, e)
            p failure new OAuthNotAuthorizedException()
        }
      case None => p failure new OAuthNotAuthorizedException()
    }
    p.future
  }

  def retrieveRequestToken(callback:String) = oauth.retrieveRequestToken(callback)

  def redirectUrl(token:String) = oauth.redirectUrl(token)

  def sessionTokenPair(implicit request: RequestHeader): Option[RequestToken] = {
    for {
      token <- request.session.get("token")
      secret <- request.session.get("secret")
    } yield {
      RequestToken(token, secret)
    }
  }

  def sessionUser(tokenPair:Option[RequestToken], create:Boolean=false)
                 (implicit request:RequestHeader) : Future[Option[User]] = {
    val p = Promise[Option[User]]
    val userId = request.session.get("userId")
    val osmId = request.session.get("osmId")
    tokenPair match {
      case Some(pair) => getUser(pair, userId, create) onComplete {
        case Success(optionUser) => p success optionUser
        case Failure(f) => p failure f
      }
      case None =>
        request.headers.get("apiKey") match {
          case Some(apiKey) =>
            val accessToken = Crypto.decryptAES(apiKey).split("|")
            getUser(RequestToken(accessToken(0), accessToken(1)), Some(accessToken(2)), create) onComplete {
              case Success(optionUser) => p success optionUser
              case Failure(f) => p failure f
            }
          case None => p success None
        }
    }
    p.future
  }

  private def getUser(accessToken:RequestToken, userId:Option[String], create:Boolean=false) : Future[Option[User]] = {
    val p = Promise[Option[User]]
    // we use the userId for caching, so only if this is the first time the user is authorizing
    // in a particular session will it have to hit the database.
    val storedUser = userId match {
      case Some(sessionId) => UserDAL.matchByRequestTokenAndId(sessionId.toLong, accessToken)
      case None => UserDAL.matchByRequestToken(accessToken)
    }
    storedUser match {
      case Some(u) => p success Some(u)
      case None =>
        if (create) {
          // if no user is matched, then lets create a new user
          val details = WS.url(userDetailsURL).sign(OAuthCalculator(consumerKey, accessToken))
          details.get() onComplete {
            case Success(detailsResponse) if detailsResponse.status == HttpResponseStatus.OK.code() =>
              val newUser = User(detailsResponse.body, accessToken)
              UserDAL.create(newUser) match {
                case Some(u) => p success Some(u)
                case None => p failure new OAuthNotAuthorizedException("Failed to create new user")
              }
            case Success(response) =>
              p failure new OAuthNotAuthorizedException()
            case Failure(error) =>
              Logger.error(error.getMessage, error)
              p failure new OAuthNotAuthorizedException()
          }
        } else {
          p success None
        }
    }
    p.future
  }

  /**
    * For a user aware request we are simply checking to see if we can find a user that can be
    * associated with the current session. So if a session or token is available we will try to authenticate
    * the user and optionally return a User object.
    *
    * @param block The block of code that is executed after user has been checked
    * @param request The incoming http request
    * @return The result from the block of code
    */
  def userAwareRequest(block:Option[User] => Result)(implicit request:Request[Any]) : Future[Result] = {
    val p = Promise[Result]
    sessionUser(sessionTokenPair) onComplete {
      case Success(result) => p success block(result)
      case Failure(error) => p failure error
    }
    p.future
  }

  /**
    * For an authenticated request we expect there to currently be a valid session. If no session
    * is available we will deny access and redirect user to login via OSM.
    *
    * @param block The block of code to execute after a valid session has been found
    * @param request The incoming http request
    * @return The result from the block of code
    */
  def authenticatedRequest(block:User => Result)(implicit request:Request[Any]) : Future[Result] = {
    val p = Promise[Result]
    sessionUser(sessionTokenPair) onComplete {
      case Success(result) => result match {
        case Some(user) => block(user)
        case None => p failure new OAuthNotAuthorizedException()
      }
      case Failure(e) => p failure e
    }
    p.future
  }
}
