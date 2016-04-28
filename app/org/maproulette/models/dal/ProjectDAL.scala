package org.maproulette.models.dal

import java.sql.Connection
import javax.inject.{Inject, Singleton}

import anorm._
import anorm.SqlParser._
import org.maproulette.cache.CacheManager
import org.maproulette.models.{Challenge, Project}
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
  override def insert(project: Project, user:User)(implicit c:Connection=null): Project = {
    project.hasWriteAccess(user)
    cacheManager.withOptionCaching { () =>
      val newProject = withMRTransaction { implicit c =>
        SQL"""INSERT INTO projects (name, description, enabled)
              VALUES (${project.name}, ${project.description}, ${project.enabled}) RETURNING *""".as(parser.*).head
      }
      // todo: this should be in the above transaction, but for some reason the fkey won't allow it
      db.withTransaction { implicit c =>
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
  override def update(updates:JsValue, user:User)(implicit id:Long, c:Connection=null): Option[Project] = {
    cacheManager.withUpdatingCache(Long => retrieveById) { implicit cachedItem =>
      cachedItem.hasWriteAccess(user)
      withMRTransaction { implicit c =>
        val name = (updates \ "name").asOpt[String].getOrElse(cachedItem.name)
        val description = (updates \ "description").asOpt[String].getOrElse(cachedItem.description.getOrElse(""))
        val enabled = (updates \ "enabled").asOpt[Boolean].getOrElse(cachedItem.enabled)

        SQL"""UPDATE projects SET name = $name,
              description = $description,
              enabled = $enabled
              WHERE id = $id RETURNING *""".as(parser.*).headOption
      }
    }
  }


  /**
    * This is a merge update function that will update the function if it exists otherwise it will
    * insert a new item.
    *
    * @param element The element that needs to be inserted or updated. Although it could be updated,
    *                it requires the element itself in case it needs to be inserted
    * @param user    The user that is executing the function
    * @param id      The id of the element that is being updated/inserted
    * @param c       A connection to execute against
    * @return
    */
  override def mergeUpdate(element: Project, user: User)(implicit id: Long, c: Connection): Option[Project] = {
    element.hasWriteAccess(user)
    withMRTransaction { implicit c =>
      None
    }
  }

  /**
    * Gets a list of all projects that are specific managed by the supplied user
    *
    * @param user The user executing the request
    * @return A list of projects managed by the user
    */
  def listManagedProjects(user:User, limit:Int = 10, offset:Int = 0, onlyEnabled:Boolean=false, searchString:String="") : List[Project] = {
    if (user.isSuperUser) {
      list(limit, offset, onlyEnabled, searchString)
    } else {
      db.withConnection { implicit c =>
        val query =
          s"""SELECT * FROM projects p
            INNER JOIN projects_group_mapping pgm ON pgm.project_id = p.id
            WHERE ${searchField("name")} AND pgm.group_id IN ({ids}) ${enabled(onlyEnabled)}
            LIMIT ${sqlLimit(limit)} OFFSET {offset}""".stripMargin
        SQL(query).on('ss -> search(searchString), 'offset -> ParameterValue.toParameterValue(offset),
          'ids -> ParameterValue.toParameterValue(user.groups.map(_.id))(p = keyToStatement))
          .as(parser.*)
      }
    }
  }
}
