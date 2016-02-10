package controllers

import org.maproulette.data.Task
import play.api.libs.json._
import play.api.mvc._

/**
  * @author cuthbertm
  */
object TaskController extends Controller {

  implicit val taskReads: Reads[Task] = Task.jsonReader
  implicit val taskWrites: Writes[Task] = Task.jsonWriter

  def createTask() = Action(BodyParsers.parse.json) { implicit request =>
    val taskResult = request.body.validate[Task]
    taskResult.fold(
      errors => {
        BadRequest(Json.obj("status" -> "KO", "message" -> JsError.toJson(errors)))
      },
      task => {
        try {
          Task.insert(task) match {
            case Some(t) => Created(Json.toJson(t))
            case None => NoContent // we should never get here
          }
        } catch {
          case e:Exception => InternalServerError(Json.obj("status" -> "KO", "message" -> e.getMessage))
        }
      }
    )
  }

  def updateTask(id:Long) = Action(BodyParsers.parse.json) { implicit request =>
    Task.update(id, request.body) match {
      case Some(value) => Ok(Json.toJson(value))
      case None => NoContent
    }
  }

  def listPrimary(primaryTag:String) = Action {
    Ok(Json.toJson(Task.list(primaryTag, None)))
  }

  def listSecondary(primaryTag:String, secondaryTag:String) = Action {
    Ok(Json.toJson(Task.list(primaryTag, Some(secondaryTag))))
  }

  def getTask(id:Long) = Action {
    Task.retrieveById(id) match {
      case Some(value) =>
        Ok(Json.toJson(value))
      case None =>
        NoContent
    }
  }

  def deleteTask(id:Long) = Action {
    Ok(Json.obj("message" -> s"${Task.delete(id)} Tasks deleted."))
    NoContent
  }
}
