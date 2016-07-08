// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.models.dal

import java.sql.Connection
import javax.inject.{Inject, Singleton}

import anorm._
import anorm.SqlParser._
import org.maproulette.actions.Actions
import org.maproulette.cache.CacheManager
import org.maproulette.exception.InvalidException
import org.maproulette.models._
import org.maproulette.permissions.Permission
import org.maproulette.session.User
import play.api.db.Database
import play.api.libs.json.{JsValue, Json}

/**
  * @author cuthbertm
  */
@Singleton
class SurveyDAL @Inject() (override val db:Database,
                           taskDAL: TaskDAL,
                           challengeDAL: ChallengeDAL,
                           override val tagDAL: TagDAL,
                           override val permission:Permission)
  extends ParentDAL[Long, Survey, Task] with TagDALMixin[Survey] {

  // The manager for the survey cache
  override val cacheManager = new CacheManager[Long, Survey]
  // The name of the survey table
  override val tableName: String = "challenges"
  // The name of the table for it's children Tasks
  override val childTable: String = "tasks"
  // The row parser for it's children defined in the TaskDAL
  override val childParser = taskDAL.parser
  override val childColumns: String = taskDAL.retrieveColumns

  override val parser: RowParser[Survey] = {
    this.challengeDAL.parser map {
      case challenge => Survey(challenge, this.getAnswers(challenge.id))
    }
  }

  private val answerParser: RowParser[Answer] = {
    get[Long]("answers.id") ~
    get[String]("answers.answer") map {
      case id ~ answer => new Answer(id, answer)
    }
  }


  /**
    * This will retrieve the root object in the hierarchy of the object, by default the root
    * object is itself.
    *
    * @param obj This is either the id of the object, or the object itself
    * @param user
    * @param c   The connection if any
    * @return The object that it is retrieving
    */
  override def retrieveRootObject(obj: Either[Long, Survey], user: User)
                                 (implicit c:Option[Connection]=None): Option[Project] = {
    obj match {
      case Left(id) => this.challengeDAL.retrieveRootObject(Left(id), user)
      case Right(value) => this.challengeDAL.retrieveRootObject(Right(value.challenge), user)
    }
  }


  /**
    * Retrieves the answers for the survey given the survey id
    *
    * @param surveyId The id for the survey
    * @return List of answers for the survey
    */
  def getAnswers(surveyId:Long)(implicit c:Option[Connection]=None) : List[Answer] = {
    this.withMRConnection { implicit c =>
      SQL"""SELECT * FROM answers WHERE survey_id = $surveyId""".as(this.answerParser.*)
    }
  }

  /**
    * Inserts a new Challenge (survey) into the database, it will use the insert from the Challenge
    * super class and then insert all the answers into the answer table
    *
    * @param survey The survey to insert into the database
    * @return The object that was inserted into the database. This will include the newly created id
    */
  override def insert(survey: Survey, user:User)(implicit c:Option[Connection]=None): Survey = {
    if (survey.answers.size < 2) {
      throw new InvalidException("At least 2 answers required for creating a survey")
    }
    this.permission.hasWriteAccess(survey.challenge, user)
    this.withMRTransaction { implicit c =>
      val newChallenge = this.challengeDAL.insert(survey.challenge.copy(challengeType = Actions.ITEM_TYPE_SURVEY), user)
      // insert the answers into the table
      val sqlQuery = s"""INSERT INTO answers (survey_id, answer) VALUES (${newChallenge.id}, {answer})"""
      val parameters = survey.answers.map(answer => {
        Seq[NamedParameter]("answer" -> answer.answer)
      })
      BatchSql(sqlQuery, parameters.head, parameters.tail:_*).execute()
      Survey(newChallenge, this.getAnswers(newChallenge.id))
    }
  }

  /**
    * Updates a Survey. It will use the update from the Challenge super class, then delete all
    * delete/update/add new answers to the answers table
    *
    * @param updates The updates in json format
    * @param id The id of the object that you are updating
    * @return An optional object, it will return None if no object found with a matching id that was supplied
    */
  override def update(updates:JsValue, user:User)(implicit id:Long, c:Option[Connection]=None): Option[Survey] = {
    this.withMRTransaction { implicit c =>
      val updatedChallenge = (updates \ "challenge").asOpt[JsValue] match {
        case Some(c) => this.challengeDAL.update(c, user)
        case None => this.challengeDAL.update(Json.parse(s"""{"id":$id}"""), user)
      }
      implicit val answerReads = Survey.answerReads
      // list of answers to delete
      (updates \ "answers" \ "delete").asOpt[List[Long]] match {
        case Some(values) => SQL"""DELETE FROM answers WHERE id IN ($values)""".execute()
        case None => //ignore
      }
      (updates \ "answers" \ "update").asOpt[List[Answer]] match {
        case Some(values) =>
          val sqlQuery = """UPDATE answers SET answer = {answer} WHERE id = {id}"""
          val parameters = values.map(answer => {
            Seq[NamedParameter]("answer" -> answer.answer, "id" -> answer.id)
          })
          BatchSql(sqlQuery, parameters.head, parameters.tail:_*).execute()
        case None => //ignore
      }
      (updates \ "answers" \ "add").asOpt[List[Answer]] match {
        case Some(values) =>
          val sqlQuery = s"""INSERT INTO answers (survey_id, answer) VALUES ($id, {answer})"""
          val parameters = values.map(answer => {
            Seq[NamedParameter]("answer" -> answer.answer)
          })
          BatchSql(sqlQuery, parameters.head, parameters.tail:_*).execute()
        case None => //ignore
      }
      Some(Survey(updatedChallenge.get, this.getAnswers(updatedChallenge.get.id)))
    }
  }

  /**
    * Answers a question by inserting a record in the survey_answers table. This will allow users
    * to answer the question for the same task multiple times. This way you will get an idea of the
    * answers based on multiple users feedback
    *
    * @param survey The survey that is being evaluated
    * @param taskId The id of the task that is viewed when answering the question
    * @param answerId The id for the selected answer
    * @param user The user answering the question, if none will default to a guest user on the database
    * @return
    */
  def answerQuestion(survey:Survey, taskId:Long, answerId:Long, user:User)(implicit c:Option[Connection]=None) : Option[Long] = {
    this.withMRTransaction { implicit c =>
      SQL"""INSERT INTO survey_answers (osm_user_id, project_id, survey_id, task_id, answer_id)
            VALUES (${user.osmProfile.id}, ${survey.challenge.parent}, ${survey.id}, $taskId, $answerId)""".executeInsert()
    }
  }

  override def find(searchString: String, limit: Int, offset: Int, onlyEnabled: Boolean,
                     orderColumn: String, orderDirection: String)
                    (implicit parentId: Long, c:Option[Connection]=None): List[Survey] =
    this.challengeDAL.findByType(searchString, limit, offset, onlyEnabled, orderColumn,
      orderDirection, Actions.ITEM_TYPE_SURVEY).map(c => Survey(c, this.getAnswers(c.id)))

  /**
    * This is a dangerous function as it will return all the objects available, so it could take up
    * a lot of memory
    */
  override def list(limit: Int, offset: Int, onlyEnabled: Boolean, searchString: String,
                    orderColumn: String, orderDirection: String)
                   (implicit parentId: Long, c:Option[Connection]=None): List[Survey] =
    this.challengeDAL.listByType(limit, offset, onlyEnabled, searchString, orderColumn,
      orderDirection, Actions.ITEM_TYPE_SURVEY).map(c => Survey(c, this.getAnswers(c.id)))
}
