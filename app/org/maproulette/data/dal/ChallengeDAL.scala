package org.maproulette.data.dal

import anorm._
import anorm.SqlParser._
import org.maproulette.cache.CacheManager
import org.maproulette.data.{Task, Challenge}
import play.api.db.DB
import play.api.libs.json.JsValue
import play.api.Play.current

/**
  * @author cuthbertm
  */
object ChallengeDAL extends ParentDAL[Long, Challenge, Task] {
  override val cacheManager = new CacheManager[Long, Challenge]
  override val tableName: String = "challenges"
  override val childTable: String = "tasks"
  override val childParser = TaskDAL.parser
  override val childColumns: String = TaskDAL.retrieveColumns

  override val parser: RowParser[Challenge] = {
    get[Long]("challenges.id") ~
      get[String]("challenges.name") ~
      get[Option[String]]("challenges.identifier") ~
      get[Long]("challenges.parent_id") ~
      get[Option[Int]]("challenges.difficulty") ~
      get[Option[String]]("challenges.description") ~
      get[Option[String]]("challenges.blurb") ~
      get[Option[String]]("challenges.instruction") map {
      case id ~ name ~ identifier ~ parentId ~ difficulty ~ description ~ blurb ~ instruction =>
        new Challenge(id, name, identifier, parentId, difficulty, description, blurb, instruction)
    }
  }

  override def insert(challenge: Challenge): Challenge = {
    cacheManager.withOptionCaching { () =>
      DB.withTransaction { implicit c =>
        SQL"""INSERT INTO challenges (name, identifier, parent_id, difficulty, description, blurb, instruction)
              VALUES (${challenge.name}, ${challenge.identifier}, ${challenge.parent},
                      ${challenge.difficulty}, ${challenge.description}, ${challenge.blurb},
                      ${challenge.instruction}) RETURNING *""".as(parser.*).headOption
      }
    }.get
  }

  override def update(tag:JsValue)(implicit id:Long): Option[Challenge] = {
    cacheManager.withUpdatingCache(Long => retrieveById) { implicit cachedItem =>
      DB.withTransaction { implicit c =>
        val identifier = (tag \ "identifier").asOpt[String].getOrElse(cachedItem.identifier.getOrElse(""))
        val name = (tag \ "name").asOpt[String].getOrElse(cachedItem.name)
        val parentId = (tag \ "parentId").asOpt[Long].getOrElse(cachedItem.parent)
        val difficulty = (tag \ "difficulty").asOpt[Int].getOrElse(cachedItem.difficulty.getOrElse(Challenge.DIFFICULTY_EASY))
        val description =(tag \ "description").asOpt[String].getOrElse(cachedItem.description.getOrElse(""))
        val blurb = (tag \ "blurb").asOpt[String].getOrElse(cachedItem.blurb.getOrElse(""))
        val instruction =(tag \ "instruction").asOpt[String].getOrElse(cachedItem.instruction.getOrElse(""))
        val updatedChallenge = Challenge(id, name, Some(identifier), parentId,
          Some(difficulty), Some(description), Some(blurb), Some(instruction))

        SQL"""UPDATE challenges SET name = ${updatedChallenge.name},
                                    identifier = ${updatedChallenge.identifier},
                                    parent_id = ${updatedChallenge.parent},
                                    difficulty = ${updatedChallenge.difficulty},
                                    description = ${updatedChallenge.description},
                                    blurb = ${updatedChallenge.blurb},
                                    instruction = ${updatedChallenge.instruction}
              WHERE id = $id RETURNING *""".as(parser.*).headOption
      }
    }
  }

  /**
    * The summary for a challenge is the status with the number of tasks associated with each status
    * underneath the given challenge
    *
    * @param id The id for the challenge
    * @return Map of status codes mapped to task counts
    */
  def getSummary(id:Long) : Map[Int, Int] = {
    DB.withConnection { implicit c =>
      val summaryParser = int("count") ~ get[Option[Int]]("tasks.status") map {
        case count ~ status => status.getOrElse(0) -> count
      }
      SQL"""SELECT COUNT(*) as count, status FROM tasks WHERE parent_id = $id GROUP BY status"""
        .as(summaryParser.*).toMap
    }
  }
}
