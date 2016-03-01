package controllers

import play.api.mvc._
import play.api.Play.current

object Application extends Controller {

  lazy val logoURL = current.configuration.getString("maproulette.logo") match {
    case Some(logo) => logo
    case None => "assets/images/logo.png"// default to the Map Roulette Icon
  }

  def index = Action {
    Ok(views.html.index("MapRoulette")(views.html.main()))
  }

  def testing = Action {
    Ok(views.html.testing())
  }
}
