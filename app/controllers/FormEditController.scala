// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package controllers

import javax.inject.Inject

import org.apache.commons.lang3.StringUtils
import org.maproulette.Config
import org.maproulette.actions.{Actions, ProjectType}
import org.maproulette.controllers.ControllerHelper
import org.maproulette.exception.NotFoundException
import org.maproulette.models.{Challenge, Project, Survey, Task}
import org.maproulette.models.dal._
import org.maproulette.permissions.Permission
import org.maproulette.services.ChallengeService
import org.maproulette.session.{SessionManager, User}
import play.api.db.Database
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.Files
import play.api.libs.json.{DefaultReads, Json}
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, AnyContent, Controller, MultipartFormData}

import scala.io.Source

/**
  * @author cuthbertm
  */
class FormEditController @Inject() (val messagesApi: MessagesApi,
                                    ws:WSClient,
                                    override val webJarAssets: WebJarAssets,
                                    sessionManager:SessionManager,
                                    override val dalManager: DALManager,
                                    challengeService: ChallengeService,
                                    val config:Config,
                                    db:Database,
                                    permission: Permission) extends Controller with I18nSupport with ControllerHelper with DefaultReads {

  private val adminHeader:String = Messages("headers.administration")

  def userSettingsPost(userId:Long) : Action[AnyContent] = Action.async { implicit request =>
    implicit val settingsWrite = User.settingsWrites
    sessionManager.authenticatedUIRequest { implicit user =>
      if (!(user.isSuperUser || user.id == userId)) {
        throw new IllegalAccessException(Messages("errors.formeditcontroller.usersettingspost.illegal"))
      }
      User.settingsForm.bindFromRequest.fold(
        formWithErrors => {
          Redirect(controllers.routes.Application.profile(2))
        },
        settings => {
          dalManager.user.update(Json.obj("settings" -> Json.toJson(settings)), user)(userId)
          Redirect(controllers.routes.Application.profile(2))
        }
      )
    }
  }

  def projectFormUI(parentId:Long, itemId:Long) : Action[AnyContent] = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      val project:Project = if (itemId > -1) {
        dalManager.project.retrieveById(itemId) match {
          case Some(proj) => proj
          case None => Project.emptyProject
        }
      } else {
        Project.emptyProject
      }
      val projectForm = Project.projectForm.fill(project)
      getOkIndex(this.adminHeader, user, views.html.admin.forms.projectForm(user, parentId, projectForm, Map.empty))
    }
  }

  def projectFormPost(parentId:Long, itemId:Long) : Action[AnyContent] = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      Project.projectForm.bindFromRequest.fold(
        formWithErrors => {
          getIndex(BadRequest, this.adminHeader, user,
            views.html.admin.forms.projectForm(user, parentId, formWithErrors, Map.empty))
        },
        project => {
          val id = if (itemId > -1) {
            implicit val groupWrites = Project.groupWrites
            dalManager.project.update(Json.toJson(project)(Project.projectWrites), user)(itemId)
            itemId
          } else {
            val newProject = dalManager.project.insert(project, user)
            newProject.id
          }
          Redirect(routes.Application.adminUIProjectList()).flashing("success" -> "Project saved!")
        }
      )
    }
  }

  def cloneChallengeFormUI(parentId:Long, itemId:Long) : Action[AnyContent] = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      permission.hasWriteAccess(ProjectType(), user)(parentId)
      dalManager.challenge.retrieveById(itemId) match {
        case Some(c) =>
          val clonedChallenge = c.copy(id = -1)
          val tags = Some(dalManager.tag.listByChallenge(itemId).map(_.name))
          if (c.challengeType == Actions.ITEM_TYPE_SURVEY) {
            val surveyForm = Survey.surveyForm.fill(Survey(clonedChallenge, dalManager.survey.getAnswers(c.id)))
            getOkIndex(this.adminHeader, user,
              views.html.admin.forms.surveyForm(user, c.name, parentId, surveyForm)
            )
          } else {
            val challengeForm = Challenge.challengeForm.fill(clonedChallenge)
            getOkIndex(this.adminHeader, user,
              views.html.admin.forms.challengeForm(user, c.name, parentId, challengeForm, tags)
            )
          }
        case None =>
          throw new NotFoundException(s"No challenge found to clone matching the given id [$itemId]")
      }
    }
  }

  def challengeFormUI(parentId:Long, itemId:Long) : Action[AnyContent] = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      dalManager.project.retrieveById(parentId) match {
        case Some(p) =>
          permission.hasWriteAccess(ProjectType(), user)(parentId)
          val challenge:Challenge = if (itemId > -1) {
            dalManager.challenge.retrieveById(itemId) match {
              case Some(chal) => chal
              case None => Challenge.emptyChallenge(parentId)
            }
          } else {
            Challenge.emptyChallenge(parentId)
          }
          val challengeForm = Challenge.challengeForm.fill(challenge)
          val challengeTags = if (itemId > -1) {
            Some(dalManager.tag.listByChallenge(itemId).map(_.name))
          } else {
            None
          }
          getOkIndex(this.adminHeader, user,
            views.html.admin.forms.challengeForm(user, p.name, parentId, challengeForm, challengeTags)
          )
        case None => throw new NotFoundException(Messages("errors.application.adminUIList.notfound"))
      }
    }
  }

  def challengeFormPost(parentId:Long, itemId:Long) : Action[MultipartFormData[Files.TemporaryFile]] =
    Action.async(parse.multipartFormData) { implicit request =>
      sessionManager.authenticatedUIRequest { implicit user =>
        dalManager.project.retrieveById(parentId) match {
          case Some(p) =>
            permission.hasWriteAccess(ProjectType(), user)(parentId)
            val tags = request.body.dataParts("tags").head.split(",").toList
            Challenge.challengeForm.bindFromRequest.fold(
              formWithErrors => {
                getIndex(BadRequest, this.adminHeader, user,
                  views.html.admin.forms.challengeForm(user, p.name, parentId, formWithErrors, Some(tags))
                )
              },
              challenge => {
                val updatedChallenge = if (itemId > -1) {
                  dalManager.challenge.update(Json.toJson(challenge)(Challenge.challengeWrites), user)(itemId).get
                } else {
                  dalManager.challenge.insert(challenge, user)
                }
                // update the data in the challenge
                val rerun = request.body.dataParts.getOrElse("rerun", Vector("false")).head.toBoolean
                if (itemId < 0 || rerun) {
                  val uploadData = request.body.file("localGeoJSON") match {
                    case Some(f) if StringUtils.isNotEmpty(f.filename) => Some(Source.fromFile(f.ref.file).getLines().mkString)
                    case _ => None
                  }
                  challengeService.buildChallengeTasks(user, updatedChallenge, uploadData)
                }

                dalManager.challenge.updateItemTagNames(updatedChallenge.id, tags, user)
                Redirect(routes.Application.adminUIChildList(Actions.ITEM_TYPE_CHALLENGE_NAME, parentId)).flashing("success" -> "Project saved!")
              }
            )
          case None => throw new NotFoundException(Messages("errors.application.adminUIList.notfound"))
        }
      }
    }

  def rebuildChallenge(parentId:Long, challengeId:Long) : Action[AnyContent] = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      permission.hasWriteAccess(ProjectType(), user)(parentId)
      dalManager.challenge.retrieveById(challengeId) match {
        case Some(c) =>
          challengeService.rebuildChallengeTasks(user, c)
          Ok
        case None => throw new NotFoundException(s"No challenge found with id $challengeId")
      }
    }
  }

  def surveyFormUI(parentId:Long, itemId:Long) : Action[AnyContent] = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      dalManager.project.retrieveById(parentId) match {
        case Some(p) =>
          permission.hasWriteAccess(ProjectType(), user)(parentId)
          val survey: Survey = if (itemId > -1) {
            dalManager.survey.retrieveById(itemId) match {
              case Some(sur) => sur
              case None => Survey.emptySurvey(parentId)
            }
          } else {
            Survey.emptySurvey(parentId)
          }
          val surveyForm = Survey.surveyForm.fill(survey)
          getOkIndex(this.adminHeader, user,
            views.html.admin.forms.surveyForm(user, p.name, parentId, surveyForm)
          )
        case None => throw new NotFoundException(Messages("errors.application.adminUIList.notfound"))
      }
    }
  }

  def surveyFormPost(parentId:Long, itemId:Long) : Action[AnyContent] = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      dalManager.project.retrieveById(parentId) match {
        case Some(p) =>
          permission.hasWriteAccess(ProjectType(), user)(parentId)
          Survey.surveyForm.bindFromRequest.fold(
            formWithErrors => {
              getIndex(BadRequest, this.adminHeader, user,
                views.html.admin.forms.surveyForm(user, p.name, parentId, formWithErrors)
              )
            },
            survey => {
              if (itemId > -1) {
                implicit val answerWrites = Survey.answerWrites
                dalManager.survey.update(Json.toJson(survey)(Survey.surveyWrites), user)(itemId)
              } else {
                dalManager.survey.insert(survey, user)
              }
              Redirect(routes.Application.adminUIChildList(Actions.ITEM_TYPE_SURVEY_NAME, parentId)).flashing("success" -> "Project saved!")
            }
          )
        case None => throw new NotFoundException(Messages("errors.application.adminUIList.notfound"))
      }
    }
  }

  def taskFormUI(projectId:Long, parentId:Long, parentType:String, itemId:Long) : Action[AnyContent] = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      dalManager.project.retrieveById(projectId) match {
        case Some(p) => dalManager.challenge.retrieveById(parentId) match {
          case Some(c) =>
            permission.hasWriteAccess(ProjectType(), user)(projectId)
            val task:Task = if (itemId > -1) {
              dalManager.task.retrieveById(itemId) match {
                case Some(t) => t
                case None => Task.emptyTask(parentId)
              }
            } else {
              Task.emptyTask(parentId)
            }
            val taskForm = Task.taskForm.fill(task)
            getOkIndex(this.adminHeader, user,
              views.html.admin.forms.taskForm(projectId, p.name, parentId, c.name, parentType, taskForm)
            )
          case None => throw new NotFoundException(Messages("errors.application.adminUIList.notfound"))
        }
        case None => throw new NotFoundException(Messages("errors.application.adminUIList.notfound"))
      }
    }
  }

  def taskFormPost(projectId:Long, parentId:Long, parentType:String, itemId:Long) : Action[AnyContent] = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      dalManager.project.retrieveById(projectId) match {
        case Some(p) => dalManager.challenge.retrieveById(parentId) match {
          case Some(c) =>
            permission.hasWriteAccess(ProjectType(), user)(projectId)
            Task.taskForm.bindFromRequest.fold(
              formWithErrors => {
                getIndex(BadRequest, this.adminHeader, user,
                  views.html.admin.forms.taskForm(projectId, p.name, parentId, c.name, parentType, formWithErrors)
                )
              },
              task => {
                if (itemId > -1) {
                  dalManager.task.update(Json.toJson(task), user)(itemId)
                } else {
                  dalManager.task.insert(task, user)
                }
                Redirect(routes.Application.adminUITaskList(projectId, parentType, parentId)).flashing("success" -> "Project saved!")
              }
            )
          case None => throw new NotFoundException(Messages("errors.application.adminUIList.notfound"))
        }
        case None => throw new NotFoundException(Messages("errors.application.adminUIList.notfound"))
      }
    }
  }
}
