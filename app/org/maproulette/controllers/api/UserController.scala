package org.maproulette.controllers.api

import javax.inject.Inject

import org.maproulette.exception.NotFoundException
import org.maproulette.session.dal.UserDAL
import org.maproulette.session.{SessionManager, User, UserSettings}
import play.api.libs.json.{DefaultWrites, JsValue, Json}
import play.api.mvc.{Action, AnyContent, BodyParsers, Controller}

/**
  * @author cuthbertm
  */
class UserController @Inject() (userDAL: UserDAL, sessionManager: SessionManager) extends Controller with DefaultWrites {

  implicit val userWrites = User.userWrites

  def getUser(userId:Long) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      if (userId == user.id || userId == user.osmProfile.id) {
        Ok(Json.toJson(user))
      } else if (user.isSuperUser) {
        this.userDAL.retrieveByOSMID(userId, user) match {
          case Some(u) => Ok(Json.toJson(u))
          case None => this.userDAL.retrieveById(userId) match {
            case Some(u) => Ok(Json.toJson(u))
            case None => throw new NotFoundException(s"No user found with id '$userId'")
          }
        }
      } else {
        throw new IllegalAccessException("Only super users have access to other user account information")
      }
    }
  }

  def getUserByOSMUsername(username:String) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      if (user.name == username) {
        Ok(Json.toJson(user))
      } else {
        // we don't need to check access here as the API only allows super users to make the call,
        // so if not a super user, the correct IllegalAccessException will be thrown
        this.userDAL.retrieveByOSMUsername(username, user) match {
          case Some(u) => Ok(Json.toJson(u))
          case None => throw new NotFoundException(s"No user found with OSM username '$username'")
        }
      }
    }
  }

  def updateUser(id:Long) : Action[JsValue] = Action.async(BodyParsers.parse.json) { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      // if the request body contains the API Key for update, let's update that separately
      (request.body \ "apiKey").asOpt[String] match {
        case Some(key) => userDAL.update(Json.parse(s"""{"apiKey":"$key"}"""), user)(id)
        case None => //just ignore, we don't have to do anything if it isn't there
      }
      implicit val settingsRead = User.settingsReads
      userDAL.managedUpdate(request.body.as[UserSettings], user)(id) match {
        case Some(u) => Ok(Json.toJson(u))
        case None => throw new NotFoundException(s"No user found to update with id '$id'")
      }
    }
  }
}
