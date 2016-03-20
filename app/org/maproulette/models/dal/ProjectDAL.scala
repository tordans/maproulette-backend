package org.maproulette.models.dal

import javax.inject.{Inject, Singleton}

import anorm._
import anorm.SqlParser._
import org.maproulette.cache.CacheManager
import org.maproulette.models.{Challenge, Project, Survey}
import org.maproulette.session.{Group, User}
import org.maproulette.session.dal.UserGroupDAL
import play.api.db.Database
import play.api.libs.json.JsValue

/**
  * Specific functions for the project data access layer
  *
  * @author cuthbertm
  */
@Singleton
class ProjectDAL @Inject() (override val db:Database,
                            childDAL:ChallengeDAL,
                            surveyDAL:SurveyDAL,
                            userGroupDAL: UserGroupDAL)
  extends ParentDAL[Long, Project, Challenge] {

  // manager for the cache of the projects
  override val cacheManager = new CacheManager[Long, Project]
  // table name for projects
  override val tableName: String = "projects"
  // table name for project children, challenges
  override val childTable: String = "challenges"
  // anorm row parser for child as defined by the challenge data access layer
  override val childParser = childDAL.parser

  // The anorm row parser for the Project to map database records directly to Project objects
  override val parser: RowParser[Project] = {
    get[Long]("projects.id") ~
      get[String]("projects.name") ~
      get[Option[String]]("projects.description") map {
      case id ~ name ~ description =>
        new Project(id, name, description, userGroupDAL.getProjectGroups(id))
    }
  }

  /**
    * Inserts a new project object into the database
    *
    * @param project The project to insert into the database
    * @return The object that was inserted into the database. This will include the newly created id
    */
  override def insert(project: Project, user:User): Project = {
    project.hasWriteAccess(user)
    cacheManager.withOptionCaching { () =>
      db.withTransaction { implicit c =>
        val newProject = SQL"""INSERT INTO projects (name, description)
              VALUES (${project.name}, ${project.description}) RETURNING *""".as(parser.*).head
        // Every new project needs to have a admin group created for them
        userGroupDAL.createGroup(project.name + "_Admin", Group.TYPE_ADMIN)
        Some(newProject)
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
  override def update(updates:JsValue, user:User)(implicit id:Long): Option[Project] = {
    cacheManager.withUpdatingCache(Long => retrieveById) { implicit cachedItem =>
      cachedItem.hasWriteAccess(user)
      db.withTransaction { implicit c =>
        val name = (updates \ "name").asOpt[String].getOrElse(cachedItem.name)
        val description = (updates \ "description").asOpt[String].getOrElse(cachedItem.description.getOrElse(""))
        val updatedProject = Project(id, name, Some(description))

        SQL"""UPDATE projects SET name = ${updatedProject.name},
              description = ${updatedProject.description}
              WHERE id = $id RETURNING *""".as(parser.*).headOption
      }
    }
  }

  /**
    * Gets a list of all projects that are specific managed by the supplied user
    *
    * @param user The user executing the request
    * @return A list of projects managed by the user
    */
  def listManagedProjects(user:User, limit:Int = 10, offset:Int = 0) : List[Project] = {
    if (user.isSuperUser) {
      list(limit, offset)
    } else {
      db.withConnection { implicit c =>
        val sqlLimit = if (limit < 0) "ALL" else limit + ""
        val query =
          s"""SELECT * FROM projects p
            INNER JOIN projects_group_mapping pgm ON pgm.project_id = p.id
            WHERE pgm.group_id IN ({ids})
            LIMIT $sqlLimit OFFSET {offset}""".stripMargin
        SQL(query).on('offset -> ParameterValue.toParameterValue(offset),
          'ids -> ParameterValue.toParameterValue(user.groups.map(_.id))(p = keyToStatement))
          .as(parser.*)
      }
    }
  }

  def listSurveys(limit:Int=10, offset:Int = 0)(implicit id:Long) : List[Survey] = {
    // add a child caching option that will keep a list of children for the parent
    db.withConnection { implicit c =>
      val sqlLimit = if (limit < 0) "ALL" else limit+""
      val query = s"SELECT * FROM surveys WHERE parent_id = {id} LIMIT $sqlLimit OFFSET {offset}"
      SQL(query).on('id -> ParameterValue.toParameterValue(id)(p = keyToStatement), 'offset -> offset).as(surveyDAL.parser.*)
    }
  }
}
