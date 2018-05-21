// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package controllers

import javax.inject.Inject
import org.maproulette.models.dal.TaskDAL
import org.maproulette.session.{SearchParameters, SessionManager, User}
import org.maproulette.utils.Utils
import play.api.mvc._

/**
  * @author cuthbertm
  */
class MappingController @Inject() (components: ControllerComponents,
                                   sessionManager:SessionManager,
                                   taskDAL: TaskDAL) extends AbstractController(components) {

  /**
    * Will return the specific geojson for the requested task
    *
    * @param taskId The id of the task that contains the geojson
    * @return The geojson
    */
  def getTaskDisplayGeoJSON(taskId:Long) : Action[AnyContent] = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      Ok(Utils.getResponseJSONNoLock(taskDAL.retrieveById(taskId), taskDAL.getLastModifiedUser))
    }
  }

  /**
    * Gets a random next task based on the user selection criteria, which contains a lot of
    * different criteria for the search.
    *
    * @return
    */
  def getRandomNextTask(proximityId:Long) : Action[AnyContent] = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      SearchParameters.withSearch { params =>
        Ok(Utils.getResponseJSONNoLock(taskDAL.getRandomTasks(User.userOrMocked(user), params, 1, None,
          Utils.negativeToOption(proximityId)).headOption, taskDAL.getLastModifiedUser))
      }
    }
  }

  /**
    * Gets a random next task based on the user selection criteria, which contains a lot of
    * different criteria for the search. Includes Priority
    *
    * @return
    */
  def getRandomNextTaskWithPriority(proximityId:Long) : Action[AnyContent] = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      SearchParameters.withSearch { params =>
        Ok(Utils.getResponseJSONNoLock(taskDAL.getRandomTasksWithPriority(User.userOrMocked(user), params, 1,
          Utils.negativeToOption(proximityId)).headOption, taskDAL.getLastModifiedUser))
      }
    }
  }

  /**
    * Retrieve the JSON for the next task in the sequence for a particular parent (Challenge or Survey)
    *
    * @param parentId The parent (challenge or survey)
    * @param currentTaskId The current task
    * @return An OK response with the task json
    */
  def getSequentialNextTask(parentId:Long, currentTaskId:Long) : Action[AnyContent] = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      SearchParameters.withSearch { params =>
        Ok(Utils.getResponseJSON(taskDAL.getNextTaskInSequence(parentId, currentTaskId,
          Some(params.taskStatus.getOrElse(List.empty))), taskDAL.getLastModifiedUser))
      }
    }
  }

  /**
    * Retrieve the JSON for the previous task in the sequence for a particular parent (Challenge or Survey)
    *
    * @param parentId The parent (challenge or survey)
    * @param currentTaskId The current task
    * @return An OK response with the task json
    */
  def getSequentialPreviousTask(parentId:Long, currentTaskId:Long) : Action[AnyContent] = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      SearchParameters.withSearch { params =>
        Ok(Utils.getResponseJSON(taskDAL.getPreviousTaskInSequence(parentId, currentTaskId,
          Some(params.taskStatus.getOrElse(List.empty))), taskDAL.getLastModifiedUser))
      }
    }
  }
}
