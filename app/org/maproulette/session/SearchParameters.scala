package org.maproulette.session

import java.net.URLDecoder

import play.api.libs.json.{Json, Reads, Writes}
import play.api.mvc.{AnyContent, Request}

/**
  * This holds the search parameters that are used to define task sets for retrieving random tasks
  * from the database
  *
  * TODO add spatial filters as well
  *
  * @author cuthbertm
  */
case class SearchParameters(projectId:Option[Long]=None,
                            projectSearch:String="",
                            projectEnabled:Boolean=true,
                            challengeId:Option[Long]=None,
                            challengeTags:List[String]=List.empty,
                            challengeSearch:String="",
                            challengeEnabled:Boolean=true,
                            taskTags:List[String]=List.empty,
                            taskSearch:String="",
                            props:Map[String, String]=Map.empty,
                            priority:Option[Int]=None) {
  def getProjectId = projectId match {
    case Some(v) if v == -1 => None
    case _ => projectId
  }

  def getChallengeId = challengeId match {
    case Some(v) if v == -1 => None
    case _ => challengeId
  }

  def getPriority = priority match {
    case Some(v) if v == -1 => None
    case _ => priority
  }
}

object SearchParameters {

  implicit val paramsWrites: Writes[SearchParameters] = Json.writes[SearchParameters]
  implicit val paramsReads: Reads[SearchParameters] = Json.reads[SearchParameters]

  def convert(value:String) : SearchParameters =
    Json.parse(URLDecoder.decode(value, "UTF-8")).as[SearchParameters]

  /**
    * Retrieves the search cookie from the cookie list and creates a search parameter object
    * to send along with the request
    *
    * @param block The block of code to be executed after the cookie has been retrieved
    * @param request The request that the cookie came in on
    * @tparam T The response type from the block of code
    * @return The response from the block of code
    */
  def withSearch[T](block:SearchParameters => T)(implicit request:Request[AnyContent]) : T = {
    val params = request.cookies.get("search") match {
      case Some(c) => Json.parse(URLDecoder.decode(c.value, "UTF-8")).as[SearchParameters]
      case None => SearchParameters()
    }
    block(params)
  }
}
