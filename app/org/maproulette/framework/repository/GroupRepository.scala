package org.maproulette.framework.repository

import java.sql.Connection

import anorm.Macro.ColumnNaming
import anorm._
import javax.inject.{Inject, Singleton}
import org.maproulette.framework.model.Group
import org.maproulette.framework.psql.Query
import org.maproulette.framework.psql.filter.{BaseFilterParameter, FilterParameter, SubQueryFilter}
import play.api.db.Database

/**
  * @author mcuthbert
  */
@Singleton
class GroupRepository @Inject() (val db: Database) extends RepositoryMixin {

  /**
    * Retrieves a single Group matching the given id
    *
    * @param id The id for the group
    * @param c An implicit connection, defaults to None
    * @return The group matching the id, None if not found
    */
  def retrieve(id: Long)(implicit c: Option[Connection] = None): Option[Group] = {
    this.withMRTransaction { implicit c =>
      Query
        .simple(List(BaseFilterParameter(Group.FIELD_ID, id)))
        .build("SELECT * FROM groups")
        .as(GroupRepository.parser.*)
        .headOption
    }
  }

  /**
    * Finds a list of groups matching the criteria given by the psqlQuery
    *
    * @param query The psqlQuery including filter, paging and ordering
    * @param c An implicit connection, defaults to None
    * @return A list of groups matching the psqlQuery criteria
    */
  def query(query: Query)(implicit c: Option[Connection] = None): List[Group] = {
    this.withMRTransaction { implicit c =>
      query.build("SELECT * FROM groups").as(GroupRepository.parser.*)
    }
  }

  /**
    * Gets all the groups that a user belongs too
    *
    * @param osmUserId The OSM user if of the user that belongs to all the groups
    * @return A list of groups belonging to the user
    */
  def getUserGroups(osmUserId: Long): List[Group] = {
    withMRTransaction { implicit c =>
      Query
        .simple(List(BaseFilterParameter(Group.FIELD_UG_OSM_USER_ID, osmUserId)))
        .build("""SELECT * FROM groups g 
           INNER JOIN user_groups ug ON ug.group_id = g.id""")
        .as(GroupRepository.parser.*)
    }
  }

  /**
    * Inserts a group into the database
    *
    * @param group The group to insert into the database. If id is set on the object it will be ignored.
    * @param c An implicit connection, defaults to None
    * @return A list of groups matching the psqlQuery criteria
    */
  def insert(group: Group)(implicit c: Option[Connection] = None): Group = {
    this.withMRTransaction { implicit c =>
      SQL"""INSERT INTO groups (project_id, name, group_type)
           VALUES ({projectId}, {name}, {groupType}) RETURNING *"""
        .on(
          Symbol("projectId") -> group.projectId,
          Symbol("name")      -> group.name,
          Symbol("groupType") -> group.groupType
        )
        .as(GroupRepository.parser.*)
        .head
    }
  }

  /**
    * Updates a group, the only value you can update in a group is the name
    *
    * @param group The group to update, which will include the name variable
    * @param c An implicit connection, that defaults to None
    * @return The updated group
    */
  def update(group: Group)(implicit c: Option[Connection] = None): Group = {
    this.withMRTransaction { implicit c =>
      SQL"""UPDATE groups SET name = {name} WHERE id = {id} RETURNING *"""
        .on(Symbol("name") -> group.name, Symbol("id") -> group.id)
        .as(GroupRepository.parser.*)
        .head
    }
  }

  /**
    * Deletes a group from the database
    *
    * @param id The id for the group
    * @param c An implicit connection, that defaults to None
    * @return true if successfully deleted
    */
  def delete(id: Long)(implicit c: Option[Connection] = None): Boolean = {
    this.withMRTransaction { implicit c =>
      Query
        .simple(List(BaseFilterParameter(Group.FIELD_ID, id)))
        .build("DELETE FROM groups")
        .execute()
    }
  }

  /**
    * Adds a user to a specific group
    *
    * @param osmId The OSM id of the user to add
    * @param groupId The identifier of the group to add the user too
    * @param c An implicit connection
    * @return true if successful
    */
  def addUserToGroup(osmId: Long, groupId: Long)(implicit c: Option[Connection] = None): Boolean = {
    this.withMRTransaction { implicit c =>
      SQL"""INSERT INTO user_groups (osm_user_id, group_id) VALUES ({osmId}, {groupId})"""
        .on(Symbol("osmId") -> osmId, Symbol("groupId") -> groupId)
        .execute()
    }
  }

  def addUserToProject(osmID: Long, groupType: Int, projectId: Long)(
      implicit c: Option[Connection] = None
  ): Boolean = {
    this.withMRTransaction { implicit c =>
      SQL("""INSERT INTO user_groups (osm_user_id, group_id)
            SELECT {id}, id FROM groups
            WHERE group_type = {type} AND project_id = {pid}
         """)
        .on(Symbol("id") -> osmID, Symbol("type") -> groupType, Symbol("pid") -> projectId)
        .execute()
    }
  }

  def deleteUserFromGroup(osmId: Long, groupId: Long)(
      implicit c: Option[Connection] = None
  ): Boolean = {
    this.withMRTransaction { implicit c =>
      Query
        .simple(
          List(
            BaseFilterParameter(Group.FIELD_UG_OSM_USER_ID, osmId),
            BaseFilterParameter(Group.FIELD_UG_GROUP_ID, groupId)
          )
        )
        .build("DELETE FROM user_groups")
        .execute()
    }
  }

  def removeUserFromProjectGroups(osmID: Long, projectId: Long, groupType: Int)(
      implicit c: Option[Connection] = None
  ): Boolean = {
    this.withMRTransaction { implicit c =>
      Query
        .simple(
          List(
            BaseFilterParameter(Group.FIELD_UG_OSM_USER_ID, osmID),
            SubQueryFilter(
              Group.FIELD_UG_GROUP_ID,
              Query.simple(
                List(
                  BaseFilterParameter(Group.FIELD_PROJECT_ID, projectId),
                  FilterParameter.conditional(
                    Group.FIELD_GROUP_TYPE,
                    groupType,
                    includeOnlyIfTrue = groupType != -1
                  )
                ),
                "SELECT id FROM groups"
              )
            )
          )
        )
        .build("DELETE FROM user_groups")
        .execute()
    }
  }
}

object GroupRepository {
  // The anorm row parser to convert group records from the database to group objects
  val parser: RowParser[Group] = Macro.namedParser[Group](ColumnNaming.SnakeCase)
}
