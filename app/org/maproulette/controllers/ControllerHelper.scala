package org.maproulette.controllers

import org.maproulette.Config
import org.maproulette.session.User
import play.api.i18n.Messages
import play.api.mvc.{Controller, Request}
import play.twirl.api.Html

/**
  * @author cuthbertm
  */
trait ControllerHelper {
  this:Controller =>

  implicit val config:Config

  protected def getOkIndex(title:String, user:User, content:Html)
                          (implicit request:Request[Any], messages:Messages) = {
    getIndex(Ok, title, user, content)
  }

  protected def getIndex(status:Status, title:String, user:User, content:Html)
                        (implicit request:Request[Any], messages:Messages) = {
    status(views.html.index(title, user, config)(content))
  }
}
