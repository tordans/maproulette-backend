package org.maproulette.data.dal

import anorm._
import anorm.SqlParser._
import org.maproulette.cache.CacheManager
import org.maproulette.data.{Task, Challenge}
import org.maproulette.utils.Utils
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
      get[Long]("challenges.parent_id") ~
      get[Option[String]]("challenges.description") ~
      get[Option[String]]("challenges.blurb") ~
      get[Option[String]]("challenges.instruction") map {
      case id ~ name ~ parentId ~ description ~ blurb ~ instruction =>
        new Challenge(id, name, parentId, description, blurb, instruction)
    }
  }

  override def insert(challenge: Challenge): Challenge = {
    cacheManager.withOptionCaching { () =>
      DB.withTransaction { implicit c =>
        SQL"""INSERT INTO challenges (name, parent_id, description, blurb, instruction)
              VALUES (${challenge.name}, ${challenge.parent}, ${challenge.description},
          ${challenge.blurb}, ${challenge.instruction}) RETURNING *""".as(parser *).headOption
      }
    }.get
  }

  override def update(tag:JsValue)(implicit id:Long): Option[Challenge] = {
    cacheManager.withUpdatingCache(Long => retrieveById) { implicit cachedItem =>
      DB.withConnection { implicit c =>
        val name = Utils.getDefaultOption((tag \ "name").asOpt[String], cachedItem.name)
        val parentId = Utils.getDefaultOption((tag \ "parentId").asOpt[Long], cachedItem.parent)
        val description =
          Utils.getDefaultOption((tag \ "description").asOpt[String], cachedItem.description, "")
        val blurb = Utils.getDefaultOption((tag \ "blurb").asOpt[String], cachedItem.blurb, "")
        val instruction =
          Utils.getDefaultOption((tag \ "instruction").asOpt[String], cachedItem.instruction, "")
        val updatedChallenge = Challenge(id, name, parentId, Some(description), Some(blurb), Some(instruction))

        SQL"""UPDATE challenges SET name = ${updatedChallenge.name},
                                    parent_id = ${updatedChallenge.parent},
                                    description = ${updatedChallenge.description},
                                    blurb = ${updatedChallenge.blurb},
                                    instruction = ${updatedChallenge.instruction}
              WHERE id = $id RETURNING *""".as(parser *).headOption
      }
    }
  }
}
