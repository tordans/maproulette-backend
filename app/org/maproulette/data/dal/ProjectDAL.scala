package org.maproulette.data.dal

import anorm._
import anorm.SqlParser._
import org.maproulette.cache.CacheManager
import org.maproulette.data.{Challenge, Project}
import org.maproulette.utils.Utils
import play.api.db.DB
import play.api.libs.json.JsValue
import play.api.Play.current

/**
  * @author cuthbertm
  */
object ProjectDAL extends ParentDAL[Long, Project, Challenge] {
  override val cacheManager = new CacheManager[Long, Project]
  override val tableName: String = "projects"
  override val childTable: String = "challenges"
  override val childParser = ChallengeDAL.parser

  override val parser: RowParser[Project] = {
    get[Long]("projects.id") ~
      get[String]("projects.name") ~
      get[Option[String]]("projects.description") map {
      case id ~ name ~ description =>
        new Project(id, name, description)
    }
  }

  override def insert(tag: Project): Project = {
    cacheManager.withOptionCaching { () =>
      DB.withTransaction { implicit c =>
        SQL"""INSERT INTO projects (name, description)
              VALUES (${tag.name}, ${tag.description}) RETURNING *""".as(parser *).headOption
      }
    }.get
  }

  override def update(tag:JsValue)(implicit id:Long): Option[Project] = {
    cacheManager.withUpdatingCache(Long => retrieveById) { implicit cachedItem =>
      DB.withConnection { implicit c =>
        val name = Utils.getDefaultOption((tag \ "name").asOpt[String], cachedItem.name)
        val description = Utils.getDefaultOption(
          (tag \ "description").asOpt[String],
          Utils.getDefaultOption(cachedItem.description, "")
        )
        val updatedProject = Project(id, name, Some(description))

        SQL"""UPDATE projects SET name = ${updatedProject.name},
              description = ${updatedProject.description}
              WHERE id = $id RETURNING *""".as(parser *).headOption
      }
    }
  }
}
