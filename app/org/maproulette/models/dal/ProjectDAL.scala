package org.maproulette.models.dal

import javax.inject.{Inject, Singleton}

import anorm._
import anorm.SqlParser._
import org.maproulette.cache.CacheManager
import org.maproulette.models.{Challenge, ChildObject, Project, Survey}
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
      get[Option[String]]("projects.description") ~
      get[Boolean]("projects.enabled") map {
      case id ~ name ~ description ~ enabled =>
        new Project(id, name, description, userGroupDAL.getProjectGroups(id), enabled)
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
        val newProject = SQL"""INSERT INTO projects (name, description, enabled)
              VALUES (${project.name}, ${project.description}, ${project.enabled}) RETURNING *""".as(parser.*).head
        // Every new project needs to have a admin group created for them
        userGroupDAL.createGroup(newProject.id, newProject.name + "_Admin", Group.TYPE_ADMIN)
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
        val enabled = (updates \ "enabled").asOpt[Boolean].getOrElse(cachedItem.enabled)

        SQL"""UPDATE projects SET name = ${name},
              description = ${description},
              enabled = ${enabled}
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

  /**
    * Lists the children survey for a specified project
    *
    * @param limit limit the number of children in the response, default 10
    * @param offset The paging, defaults to the first page 0, second page 1, etc.
    * @param id The id for the parent project
    * @return A list of survey objects that are children of the project
    */
  def listSurveys(limit:Int=10, offset:Int = 0, onlyEnabled:Boolean=false)(implicit id:Long) : List[Survey] = {
    // add a child caching option that will keep a list of children for the parent
    db.withConnection { implicit c =>
      val sqlLimit = if (limit < 0) "ALL" else limit+""
      val enabledString = if (onlyEnabled) "AND enabled = TRUE" else ""
      val query = s"SELECT * FROM surveys WHERE parent_id = {id} $enabledString LIMIT $sqlLimit OFFSET {offset}"
      SQL(query).on('id -> ParameterValue.toParameterValue(id)(p = keyToStatement), 'offset -> offset).as(surveyDAL.parser.*)
    }
  }

  /**
    * Adds a user as a admin of a project. Although on the backend there is support in place for
    * multiple groups which in theory would enable different levels of access to the project, currently
    * there is only a requirement for administrators of the project, and implementing more would simply
    * add way too much complexity for little to no gain. In the future the base support is there
    * however to move in that direction if a valid use case arises.
    *
    * @param projectId The project to add the user too
    * @param userId The id of the user to add to the project
    * @param user The user executing the request, for this action a super user is required
    */
  def addUserToProject(projectId:Long, userId:Long, user:User) : Unit = {
    if (user.isSuperUser) {
      db.withConnection { implicit c =>

      }
    } else {
      throw new IllegalAccessException(s"User ${user.name} does not have access to add users to projects.")
    }
  }
}
