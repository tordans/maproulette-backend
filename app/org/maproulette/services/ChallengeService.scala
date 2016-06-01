package org.maproulette.services

import java.util.UUID
import javax.inject.{Inject, Singleton}

import org.apache.commons.lang3.StringUtils
import org.maproulette.Config
import org.maproulette.exception.InvalidException
import org.maproulette.models.{Challenge, Task}
import org.maproulette.models.dal.{ChallengeDAL, TaskDAL}
import org.maproulette.session.User
import play.api.Logger
import play.api.db.Database
import play.api.http.Status
import play.api.libs.json._
import play.api.libs.ws.WSClient

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

/**
  * @author cuthbertm
  */
@Singleton
class ChallengeService @Inject() (challengeDAL: ChallengeDAL, taskDAL: TaskDAL,
                                  config:Config, ws:WSClient, db:Database) extends DefaultReads {
  import scala.concurrent.ExecutionContext.Implicits.global

  def rebuildChallengeTasks(user:User, challenge:Challenge) = buildChallengeTasks(user, challenge)

  def buildChallengeTasks(user:User, challenge:Challenge, json:Option[String]=None) = {
    if (!challenge.overpassQL.getOrElse("").isEmpty) {
      Future {
        Logger.debug("Creating tasks for overpass query: " + challenge.overpassQL.get)
        buildOverpassQLTasks(challenge, user)
      }
    } else {
      val usingLocalJson = json match {
        case Some(value) if StringUtils.isNotEmpty(value) =>
          Future {
            Logger.debug("Creating tasks from local GeoJSON file")
            createTasksFromFeatures(user, challenge, Json.parse(value))
          }
          true
        case _ => false
      }
      if (!usingLocalJson) {
        // lastly try remote
        challenge.remoteGeoJson match {
          case Some(url) if StringUtils.isNotEmpty(url) =>
            ws.url(url).withRequestTimeout(config.getOSMQLProvider.requestTimeout).get() onComplete {
              case Success(resp) =>
                Logger.debug("Creating tasks from remote GeoJSON file")
                createTasksFromFeatures(user, challenge, Json.parse(resp.body))
              case Failure(f) =>
                challengeDAL.update(Json.obj("overpassStatus" -> Challenge.STATUS_FAILED), user)(challenge.id)
            }
          case None => // just do nothing
        }
      }
    }
  }

  private def createTasksFromFeatures(user:User, parent:Challenge, jsonData:JsValue) = {
    challengeDAL.update(Json.obj("status" -> Challenge.STATUS_BUILDING), user)(parent.id)
    val featureList = (jsonData \ "features").as[List[JsValue]]
    featureList.map { value =>
      val name = (value \ "id").asOpt[String] match {
        case Some(n) => n
        case None => (value \ "name").asOpt[String] match {
          case Some(na) => na
          case None =>
            // if we still don't find anything, create a UUID for it.
            // The caveat to this is that if you upload the same file again, it will create
            // duplicate tasks
            UUID.randomUUID().toString
        }
      }
      createNewTask(user, name, parent, (value \ "geometry").as[JsObject], getProperties(value, "properties"))
    }
    challengeDAL.update(Json.obj("status" -> Challenge.STATUS_COMPLETE), user)(parent.id)
    Logger.debug(s"${featureList.size} tasks created from json file.")
  }

  /**
    * Based on the supplied overpass query this will generate the tasks for the challenge
    *
    * @param challenge The challenge to create the tasks under
    * @param user The user executing the query
    */
  private def buildOverpassQLTasks(challenge:Challenge, user:User) = {
    challenge.overpassQL match {
      case Some(ql) if StringUtils.isNotEmpty(ql) =>
        // run the query and then create the tasks
        val osmQLProvider = config.getOSMQLProvider
        val timeoutPattern = "\\[timeout:([\\d]*)\\]".r
        val timeout = timeoutPattern.findAllIn(ql).matchData.toList.headOption match {
          case Some(m) => Duration(m.group(1).toInt, "seconds")
          case None => osmQLProvider.requestTimeout
        }

        val jsonFuture = ws.url(osmQLProvider.providerURL).withRequestTimeout(timeout).post(parseQuery(ql))

        jsonFuture onComplete {
          case Success(result) =>
            if (result.status == Status.OK) {
              db.withTransaction { implicit c =>
                var partial = false
                val payload = result.json
                // parse the results
                val elements = (payload \ "elements").as[List[JsValue]]
                elements.foreach {
                  element =>
                    try {
                      val geometry = (element \ "type").asOpt[String] match {
                        case Some("way") =>
                          val points = (element \ "geometry").as[List[JsValue]].map {
                            geom => List((geom \ "lon").as[Double], (geom \ "lat").as[Double])
                          }
                          Some(Json.obj("type" -> "LineString", "coordinates" -> points))
                        case Some("node") =>
                          Some(Json.obj(
                            "type" -> "Point",
                            "coordinates" -> List((element \ "lon").as[Double], (element \ "lat").as[Double])
                          ))
                        case _ => None
                      }

                      geometry match {
                        case Some(geom) =>
                          createNewTask(user, (element \ "id").as[Long]+"", challenge, geom, getProperties(element, "tags"))
                        case None => None
                      }
                    } catch {
                      case e:Exception =>
                        partial = true
                        Logger.error(e.getMessage, e)
                    }
                }
                partial match {
                  case true =>
                    challengeDAL.update(Json.obj("status" -> Challenge.STATUS_PARTIALLY_LOADED), user)(challenge.id)
                  case false =>
                    challengeDAL.update(Json.obj("status" -> Challenge.STATUS_COMPLETE), user)(challenge.id)
                }
              }
            } else {
              challengeDAL.update(Json.obj("status" -> Challenge.STATUS_FAILED), user)(challenge.id)
              throw new InvalidException(s"Bad Request: ${result.body}")
            }
          case Failure(f) =>
            challengeDAL.update(Json.obj("status" -> Challenge.STATUS_FAILED), user)(challenge.id)
            throw f
        }
      case None => // just ignore, we don't have to do anything if it wasn't set
    }
  }

  private def createNewTask(user:User, name:String, parent:Challenge, geometry:JsObject,
                            properties:JsValue) : Boolean = {
    val newTask = Task(-1, name,
      parent.id,
      Some(""),
      None,
      Json.obj(
        "type" -> "FeatureCollection",
        "features" -> Json.arr(Json.obj(
          "id" -> name,
          "geometry" -> geometry,
          "properties" -> properties
        ))
      ).toString
    )

    try {
      taskDAL.mergeUpdate(newTask, user)(newTask.id)
      true
    } catch {
      // this task could fail on unique key violation, we need to ignore them
      case e:Exception =>
        Logger.error(e.getMessage)
        false
    }
  }

  /**
    * parse the query, replace various extended overpass query parameters see http://wiki.openstreetmap.org/wiki/Overpass_turbo/Extended_Overpass_Queries
    * Currently do not support {{bbox}} or {{center}}
    *
    * @param query The query to parse
    * @return
    */
  private def parseQuery(query:String) : String = {
    val osmQLProvider = config.getOSMQLProvider
    // User can set their own custom timeout if the want
    var replacedQuery = if (query.indexOf("[out:json]") == 0) {
      query
    } else if (query.indexOf("[timeout:") == 0) {
      s"[out:json]$query"
    } else {
      s"[out:json][timeout:${osmQLProvider.requestTimeout.toSeconds}];$query"
    }
    // execute regex matching against {{data:string}}, {{geocodeId:name}}, {{geocodeArea:name}}, {{geocodeBbox:name}}, {{geocodeCoords:name}}
    replacedQuery
  }

  private def getProperties(value:JsValue, key:String) : JsValue = {
    (value \ key).asOpt[JsObject] match {
      case Some(JsObject(p)) =>
        val updatedMap = p.map { kv => kv._1 -> kv._2.toString() }.toMap
        Json.toJson(updatedMap)
      case _ => Json.obj()
    }
  }
}
