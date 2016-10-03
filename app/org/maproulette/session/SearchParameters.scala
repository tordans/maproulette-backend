// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.session

import java.net.URLDecoder

import org.maproulette.utils.Utils
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{AnyContent, Request}

/**
  * This holds the search parameters that are used to define task sets for retrieving random tasks
  * from the database
  *
  * TODO add spatial filters as well
  *
  * @author cuthbertm
  */
case class SearchLocation(left:Double, bottom:Double, right:Double, top:Double)

case class SearchParameters(projectId:Option[Long]=None,
                            projectSearch:String="",
                            projectEnabled:Boolean=true,
                            challengeId:Option[Long]=None,
                            challengeTags:List[String]=List.empty,
                            challengeSearch:String="",
                            challengeEnabled:Boolean=true,
                            taskTags:List[String]=List.empty,
                            taskSearch:String="",
                            taskStatus:List[Int]=List.empty,
                            props:Option[Map[String, String]]=None,
                            priority:Option[Int]=None,
                            location:Option[SearchLocation]=None,
                            owner:Option[String]=None) {
  def getProjectId : Option[Long] = projectId match {
    case Some(v) if v == -1 => None
    case _ => projectId
  }

  def getChallengeId : Option[Long] = challengeId match {
    case Some(v) if v == -1 => None
    case _ => challengeId
  }

  def getPriority : Option[Int] = priority match {
    case Some(v) if v == -1 => None
    case _ => priority
  }
}

object SearchParameters {

  implicit val locationWrites = Json.writes[SearchLocation]
  implicit val locationReads = Json.reads[SearchLocation]
  implicit val paramsWrites = Json.writes[SearchParameters]
  implicit val paramsReads = Json.reads[SearchParameters]

  def convert(value:String) : SearchParameters =
    Utils.omitEmpty(Json.parse(URLDecoder.decode(value, "UTF-8")).as[JsObject], false, false).as[SearchParameters]

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
      case Some(c) => convert(c.value)
      case None => SearchParameters()
    }
    block(params)
  }
}
