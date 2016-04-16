package controllers

import javax.inject.Inject

import org.maproulette.exception.NotFoundException
import org.maproulette.models.Task
import org.maproulette.models.dal.TaskDAL
import org.maproulette.session.{SearchParameters, SessionManager}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, Controller}

/**
  * @author cuthbertm
  */
class MappingController @Inject() (sessionManager:SessionManager,
                                   taskDAL: TaskDAL) extends Controller {

  /**
    * Will return the specific geojson for the requested task
    *
    * @param taskId The id of the task that contains the geojson
    * @return The geojson
    */
  def getTaskDisplayGeoJSON(taskId:Long) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      Ok(getResponseJSON(taskDAL.retrieveById(taskId)))
    }
  }

  /**
    * Gets a random next task based on the user selection criteria, which contains a lot of
    * different criteria for the search.
    *
    * @param projectId filter by a project Id, use -1 if want to ignore project id
    * @param projectSearch filter by the name of the project, ignored if project Id set
    * @param challengeId filter by a challenge Id, use -1 if want to ignore challenge id
    * @param challengeTags filter by the tags on the challenge, ignored if challenge Id set
    * @param challengeSearch filter by the name of the challenge, ignored if challenge Id set
    * @param taskTags filter by the tags on the task
    * @param taskSearch filter by the name of the task
    *
    * @return
    */
  def getRandomNextTask(projectId:Long,
                        projectSearch:String,
                        challengeId:Long,
                        challengeTags:String,
                        challengeSearch:String,
                        taskTags:String,
                        taskSearch:String) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      val params = SearchParameters(
        if (projectId == -1) None else Some(projectId),
        projectSearch,
        if (challengeId == -1) None else Some(challengeId),
        challengeTags.split(",").toList,
        challengeSearch,
        taskTags.split(",").toList,
        taskSearch
      )

      Ok(getResponseJSON(taskDAL.getRandomTasks(params, 1).headOption))
    }
  }

  /**
    * Retrieve the JSON for the next task in the sequence for a particular parent (Challenge or Survey)
    *
    * @param parentId The parent (challenge or survey)
    * @param currentTaskId The current task
    * @return An OK response with the task json
    */
  def getSequentialNextTask(parentId:Long, currentTaskId:Long) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      Ok(getResponseJSON(taskDAL.getNextTaskInSequence(parentId, currentTaskId)))
    }
  }

  /**
    * Retrieve the JSON for the previous task in the sequence for a particular parent (Challenge or Survey)
    *
    * @param parentId The parent (challenge or survey)
    * @param currentTaskId The current task
    * @return An OK response with the task json
    */
  def getSequentialPreviousTask(parentId:Long, currentTaskId:Long) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      Ok(getResponseJSON(taskDAL.getPreviousTaskInSequence(parentId, currentTaskId)))
    }
  }

  /**
    * Builds the response JSON for mapping based on a Task
    *
    * @param task The optional task to check
    * @return If None supplied as Task parameter then will throw NotFoundException
    */
  private def getResponseJSON(task:Option[Task]) : JsValue = task match {
    case Some(t) =>
      Json.parse(
        s"""
           |{
           |   "id":${t.id},
           |   "parentId":${t.parent},
           |   "name":"${t.name}",
           |   "instruction":"${t.instruction}",
           |   "status":"${Task.getStatusName(t.status.getOrElse(Task.STATUS_CREATED))}",
           |   "geometry":${t.geometries}
           |}
            """.stripMargin)
    case None => throw new NotFoundException(s"Could not find task")
  }
}
