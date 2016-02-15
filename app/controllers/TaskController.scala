package controllers

import com.fasterxml.jackson.databind.JsonMappingException
import org.maproulette.data.Task
import org.maproulette.data.dal.TaskDAL
import org.maproulette.utils.Utils
import play.api.libs.json._
import play.api.mvc._

/**
  * @author cuthbertm
  */
object TaskController extends Controller {
  def createTask() = Action(BodyParsers.parse.json) { implicit request =>
    val taskResult = Utils.insertJson(request.body).validate[Task]
    taskResult.fold(
      errors => {
        BadRequest(Json.obj("status" -> "KO", "message" -> JsError.toJson(errors)))
      },
      task => {
        try {
          Created(Json.toJson(TaskDAL.insert(task)))
        } catch {
          case e:Exception => InternalServerError(Json.obj("status" -> "KO", "message" -> e.getMessage))
        }
      }
    )
  }

  def updateTask(implicit id:Long) = Action(BodyParsers.parse.json) { implicit request =>
    try {
      Ok(Json.toJson(Task.getUpdateOrCreateTask(request.body)))
    } catch {
      case e:JsonMappingException => BadRequest(Json.obj("status" -> "KO", "message" -> Json.parse(e.getMessage)))
      case e:Exception => InternalServerError(Json.obj("status" -> "KO", "message" -> e.getMessage))
    }
  }

  def list(limit:Int, offset:Int) = Action {
    Ok(Json.toJson(TaskDAL.list(None, None, limit, offset)))
  }

  def listPrimary(primaryTag:String, limit:Int, offset:Int) = Action {
    Ok(Json.toJson(TaskDAL.list(Some(primaryTag), None, limit, offset)))
  }

  def listSecondary(primaryTag:String, secondaryTag:String, limit:Int, offset:Int) = Action {
    Ok(Json.toJson(TaskDAL.list(Some(primaryTag), Some(secondaryTag), limit, offset)))
  }

  def getTask(implicit id:Long) = Action {
    TaskDAL.retrieveById match {
      case Some(value) =>
        Ok(Json.toJson(value))
      case None =>
        NoContent
    }
  }

  def deleteTask(id:Long) = Action {
    Ok(Json.obj("message" -> s"${TaskDAL.delete(id)} Tasks deleted."))
    NoContent
  }
}
