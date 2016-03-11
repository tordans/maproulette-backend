package org.maproulette.models.dal

import anorm._
import anorm.SqlParser._
import org.maproulette.cache.CacheManager
import org.maproulette.models.{Challenge, Project}
import play.api.db.DB
import play.api.libs.json.JsValue
import play.api.Play.current

/**
  * Specific functions for the project data access layer
  *
  * @author cuthbertm
  */
object ProjectDAL extends ParentDAL[Long, Project, Challenge] {
  // manager for the cache of the projects
  override val cacheManager = new CacheManager[Long, Project]
  // table name for projects
  override val tableName: String = "projects"
  // table name for project children, challenges
  override val childTable: String = "challenges"
  // anorm row parser for child as defined by the challenge data access layer
  override val childParser = ChallengeDAL.parser

  // The anorm row parser for the Project to map database records directly to Project objects
  override val parser: RowParser[Project] = {
    get[Long]("projects.id") ~
      get[String]("projects.name") ~
      get[Option[String]]("projects.description") map {
      case id ~ name ~ description =>
        new Project(id, name, description)
    }
  }

  /**
    * Inserts a new project object into the database
    *
    * @param project The project to insert into the database
    * @return The object that was inserted into the database. This will include the newly created id
    */
  override def insert(project: Project): Project = {
    cacheManager.withOptionCaching { () =>
      DB.withTransaction { implicit c =>
        SQL"""INSERT INTO projects (name, description)
              VALUES (${project.name}, ${project.description}) RETURNING *""".as(parser.*).headOption
      }
    }.get
  }

  /**
    * Updates a project in the database
    *
    * @param updates A json object containing all the fields that are too be updated.
    * @param id The id of the object that you are updating.
    * @return An optional object, it will return None if no object found with a matching id that was supplied.
    */
  override def update(updates:JsValue)(implicit id:Long): Option[Project] = {
    cacheManager.withUpdatingCache(Long => retrieveById) { implicit cachedItem =>
      DB.withTransaction { implicit c =>
        val name = (updates \ "name").asOpt[String].getOrElse(cachedItem.name)
        val description = (updates \ "description").asOpt[String].getOrElse(cachedItem.description.getOrElse(""))
        val updatedProject = Project(id, name, Some(description))

        SQL"""UPDATE projects SET name = ${updatedProject.name},
              description = ${updatedProject.description}
              WHERE id = $id RETURNING *""".as(parser.*).headOption
      }
    }
  }
}
