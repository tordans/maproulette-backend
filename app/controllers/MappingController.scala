package controllers

import javax.inject.Inject

import org.maproulette.exception.NotFoundException
import org.maproulette.models.dal.TaskDAL
import org.maproulette.session.SessionManager
import play.api.libs.json.Json
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
      val json = taskDAL.retrieveById(taskId) match {
        case Some(task) => Json.parse(task.geometries)
        case None => throw new NotFoundException(s"Could not find task with id $taskId")
      }
      Ok(json)
    }
  }
}
