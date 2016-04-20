package org.maproulette.controllers

import controllers.WebJarAssets
import org.joda.time.DateTime
import org.maproulette.Config
import org.maproulette.actions.ActionManager
import org.maproulette.models.dal.{ChallengeDAL, DALManager}
import org.maproulette.session.{SessionManager, User}
import play.api.i18n.Messages
import play.api.mvc.{Controller, Request, Result}
import play.twirl.api.Html

/**
  * Helper functions that help the controller with various http related functions
  *
  * @author cuthbertm
  */
trait ControllerHelper {
  this:Controller =>

  implicit val config:Config
  def webJarAssets : WebJarAssets
  implicit def requestWebJarAssets : WebJarAssets = webJarAssets
  val dalManager:DALManager

  /**
    * Returns an Ok status code with the primary index file that is used in MapRoulette.
    * Most UI responses will reference this index file.
    *
    * @param title The title for the index file
    * @param user The user to send back with the response
    * @param content The content of the primary UI area
    * @param request The original http request
    * @param messages Messages for I18N
    * @return The Result response for Play to handle
    */
  protected def getOkIndex(title:String, user:User, content:Html)
                          (implicit request:Request[Any], messages:Messages) : Result = {
    getIndex(Ok, title, user, content)
      .addingToSession(SessionManager.KEY_USER_TICK -> DateTime.now().getMillis.toString)
  }

  /**
    * Returns the primary index file with the supplied status code. The response will also
    * be returned with the UserTick for the particular session updated.
    *
    * @param status The status code to be returned with the index view
    * @param title The title for the index file
    * @param user The user to send back with the response
    * @param content The content of the primary UI area
    * @param request The original http request
    * @param messages Message for I18N
    * @return The Result response for Play to handle
    */
  protected def getIndex(status:Status, title:String, user:User, content:Html)
                        (implicit request:Request[Any], messages:Messages) : Result = {
    status(views.html.index(title, user, config,
      dalManager.challenge.getHotChallenges(config.numberOfChallenges, 0),
      dalManager.challenge.getNewChallenges(config.numberOfChallenges, 0),
      dalManager.challenge.getFeaturedChallenges(config.numberOfChallenges, 0),
      dalManager.action.getRecentActivity(user.id, 5, 0)
    )(content))
      .addingToSession(SessionManager.KEY_USER_TICK -> DateTime.now().getMillis.toString)
  }
}
