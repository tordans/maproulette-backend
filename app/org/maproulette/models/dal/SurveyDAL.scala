package org.maproulette.models.dal

import javax.inject.{Inject, Singleton}

import anorm._
import anorm.SqlParser._
import org.maproulette.cache.CacheManager
import org.maproulette.exception.InvalidException
import org.maproulette.models.{Answer, Task, Survey}
import org.maproulette.session.User
import play.api.db.Database
import play.api.libs.json.JsValue

/**
  * @author cuthbertm
  */
@Singleton
class SurveyDAL @Inject() (override val db:Database, taskDAL: TaskDAL) extends ParentDAL[Long, Survey, Task] {
  // The manager for the survey cache
  override val cacheManager = new CacheManager[Long, Survey]
  // The name of the survey table
  override val tableName: String = "survey"
  // The name of the table for it's children Tasks
  override val childTable: String = "tasks"
  // The row parser for it's children defined in the TaskDAL
  override val childParser = taskDAL.parser
  override val childColumns: String = taskDAL.retrieveColumns

  private val answerParser: RowParser[Answer] = {
    get[Long]("answers.id") ~
    get[String]("answers.answer") map {
      case id ~ answer => new Answer(id, answer)
    }
  }

  /**
    * The row parser for Anorm to enable the object to be read from the retrieved row directly
    * to the survey object.
    */
  override val parser: RowParser[Survey] = {
    get[Long]("surveys.id") ~
      get[String]("surveys.name") ~
      get[Option[String]]("surveys.identifier") ~
     get[Option[String]]("surveys.description") ~
      get[Long]("surveys.parent_id") ~
      get[String]("surveys.question") map {
      case id ~ name ~ identifier ~ description ~ parentId ~ question =>
        val answers = db.withTransaction { implicit c =>
          SQL"""SELECT * FROM answers WHERE survey_id = $id""".as(answerParser.*)
        }
        new Survey(id, name, identifier, description, parentId, question, answers)
    }
  }

  /**
    * Inserts a new Survey object into the database. It will also place it in the cache after
    * inserting the object.
    *
    * @param survey The survey to insert into the database
    * @return The object that was inserted into the database. This will include the newly created id
    */
  override def insert(survey: Survey, user:User): Survey = {
    if (survey.answers.size < 2) {
      throw new InvalidException("At least 2 answers required for creating a survey")
    }
    survey.hasWriteAccess(user)
    cacheManager.withOptionCaching { () =>
      db.withTransaction { implicit c =>
        val newSurveyId = SQL"""INSERT INTO survey (name, identifier, parent_id, description, question)
              VALUES (${survey.name}, ${survey.identifier}, ${survey.parent},
                      ${survey.description}, ${survey.question}) RETURNING id""".as(long("id").single)
        // insert the answers into the table
        SQL"""INSERT INTO answers (answer, survey_id) VALUES """.executeInsert()
        Some(survey.copy(id = newSurveyId))
      }
    }.get
  }

  /**
    * Updates a Survey. Uses the updatingCache so will first retrieve the object and make sure
    * to update only values supplied by the json. After updated will update the cache as well
    *
    * @param updates The updates in json format
    * @param id The id of the object that you are updating
    * @return An optional object, it will return None if no object found with a matching id that was supplied
    */
  override def update(updates:JsValue, user:User)(implicit id:Long): Option[Survey] = {
    cacheManager.withUpdatingCache(Long => retrieveById) { implicit cachedItem =>
      cachedItem.hasWriteAccess(user)
      db.withTransaction { implicit c =>
        val identifier = (updates \ "identifier").asOpt[String].getOrElse(cachedItem.identifier.getOrElse(""))
        val name = (updates \ "name").asOpt[String].getOrElse(cachedItem.name)
        val parentId = (updates \ "parentId").asOpt[Long].getOrElse(cachedItem.parent)
        val description =(updates \ "description").asOpt[String].getOrElse(cachedItem.description.getOrElse(""))
        val question = (updates \ "question").asOpt[String].getOrElse(cachedItem.question)
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

        SQL"""UPDATE survey SET name = $name,
                                    identifier = $identifier,
                                    parent_id = $parentId,
                                    description = $description,
                                    question = $question
              WHERE id = $id RETURNING *""".as(parser.*).headOption
      }
    }
  }

  /**
    * Answers a question by inserting a record in the survey_answers table. This will allow users
    * to answer the question for the same task multiple times. This way you will get an idea of the
    * answers based on multiple users feedback
    *
    * @param surveyId The survey that is being evaluated
    * @param taskId The id of the task that is viewed when answering the question
    * @param answerId The id for the selected answer
    * @param user The user answering the question, if none will default to a guest user on the database
    * @return
    */
  def answerQuestion(surveyId:Long, taskId:Long, answerId:Long, user:Option[User]=None) = {
    db.withConnection { implicit c =>
      val userId = user match {
        case Some(u) => Some(u.id)
        case None => None
      }
      SQL"""INSERT INTO survey_answers (userId, survey_id, task_id, answer_id)
            VALUES ($userId, $surveyId, $taskId, $answerId)""".executeInsert()
    }
  }
}
