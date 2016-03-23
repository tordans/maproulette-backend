package org.maproulette.session.dal

import javax.inject.Inject

import anorm._
import anorm.SqlParser._
import org.maproulette.cache.CacheManager
import org.maproulette.session.Group
import play.api.db.Database

/**
  * Data Access Layer for groups
  * todo: add permissions, although most of this will be behind the scenes
  *
  * @author cuthbertm
  */
class UserGroupDAL @Inject() (db:Database) {

  // The cache manager for the users
  val cacheManager = new CacheManager[Long, Group]

  // The anorm row parser to convert group records from the database to group objects
  val parser: RowParser[Group] = {
    get[Long]("groups.id") ~
      get[String]("groups.name") ~
      get[Long]("groups.project_id") ~
      get[Int]("groups.group_type") map {
      case id ~ name ~ projectId ~ groupType =>
        // If the modified date is too old, then lets update this user information from OSM
        new Group(id, name, projectId, groupType)
    }
  }

  /**
    * Creates a new group given a name and groupType id
    *
    * @param projectId The id of the project that this group is created under
    * @param name The name of the group to add
    * @param groupType current only 1 (Admin) however currently no restriction on what you can supply here
    * @return The new group
    */
  def createGroup(projectId:Long, name:String, groupType:Int) : Group = db.withConnection { implicit c =>
    SQL"""INSERT INTO groups (project_id, name, group_type) VALUES ($projectId, $name, $groupType) RETURNING *""".as(parser.*).head
  }

  /**
    * Update the group name, groups can only have their names modified. The project id for a group
    * cannot be updated
    *
    * @param groupId The id for the group
    * @param newName The new name of the group
    * @return The updated group
    */
  def updateGroup(groupId:Long, newName:String) : Group = db.withConnection { implicit c =>
    SQL"""UPDATE groups SET name = $newName WHERE id = $groupId RETURNING *""".as(parser.*).head
  }

  /**
    * Deletes the group
    *
    * @param groupId The id of the group to delete
    * @return 1 or 0, the number of rows deleted. Can never be more than one, 0 if no group with id found to delete
    */
  def deleteGroup(groupId:Long) : Int = db.withConnection { implicit c =>
    SQL"""DELETE FROM groups WHERE id = $groupId""".executeUpdate()
  }

  /**
    * Deletes the group
    *
    * @param name The name of the group to delete
    * @return 1 or 0, the number of rows deleted. Can never be more than one, 0 if no group with name found to delete
    */
  def deleteGroupByName(name:String) : Int = db.withConnection { implicit c =>
    SQL"""DELETE FROM groups WHERE name = $name""".executeUpdate()
  }

  /**
    * Gets all the groups that a specific user belongs too
    *
    * @param userId The id of the user
    * @return A list of groups the user belongs too
    */
  def getGroups(userId:Long) : List[Group] = db.withConnection { implicit c =>
    SQL"""SELECT * FROM groups g
          INNER JOIN user_groups ug ON ug.group_id = g.id
          WHERE ug.user_id = $userId""".as(parser.*)
  }

  /**
    * Gets all the groups that a specific project has
    *
    * @param projectId The project id
    * @return
    */
  def getProjectGroups(projectId:Long) : List[Group] = db.withConnection { implicit c =>
    SQL"""SELECT * FROM groups g WHERE project_id = $projectId""".as(parser.*)
  }
}
