package org.maproulette.models.dal

import java.sql.Connection

import anorm._
import org.maproulette.models.BaseObject
import org.maproulette.models.utils.DALHelper

/**
  * Parent data access layer that simply includes the ability to list the children of the current
  * object. Current parents are just Project and Challenge
  *
  * @author cuthbertm
  */
trait ParentDAL[Key, T<:BaseObject[Key], C<:BaseObject[Key]] extends BaseDAL[Key, T] with DALHelper {
  // The table of the child for this type
  val childTable:String
  // The anorm row parser for the child of this type
  val childParser:RowParser[C]
  // The specific columns to be retrieved for the child. This is used in the particular cases
  // where you want to retrieve derived data. Specifically data from PostGIS
  val childColumns:String = "*"

  /**
    * Lists the children of the parent
    *
    * @param limit limits the number of children to be returned
    * @param offset For paging, ie. the page number starting at 0
    * @param id The parent ID
    * @return A list of children objects
    */
  def listChildren(limit:Int=10, offset:Int=0, onlyEnabled:Boolean=false, searchString:String="",
                   orderColumn:String="id", orderDirection:String="ASC")(implicit id:Key, c:Connection=null) : List[C] = {
    // add a child caching option that will keep a list of children for the parent
    withMRConnection { implicit c =>
      val query = s"""SELECT $childColumns FROM $childTable
                      WHERE parent_id = {id} ${enabled(onlyEnabled)}
                      ${searchField("name")}
                      ${order(Some(orderColumn), orderDirection)}
                      LIMIT ${sqlLimit(limit)} OFFSET {offset}"""
      SQL(query).on('ss -> search(searchString),
                    'id -> ParameterValue.toParameterValue(id)(p = keyToStatement),
                    'offset -> offset)
        .as(childParser.*)
    }
  }

  /**
    * Lists the children of the parent based on the parents name
    *
    * @param limit limits the number of children to be returned
    * @param offset For paging, ie. the page number starting at 0
    * @param name The parent name
    * @return A list of children objects
    */
  def listChildrenByName(limit:Int=10, offset:Int=0, onlyEnabled:Boolean=false, searchString:String="",
                         orderColumn:String="id", orderDirection:String="ASC")(implicit name:String, c:Connection=null) : List[C] = {
    // add a child caching option that will keep a list of children for the parent
    withMRConnection { implicit c =>
      // TODO currently it will only check if the parent is enabled and not the child, this is because
      // there is the case where a Task is a child of challenge and so there is no enabled column on that table
      val query = s"""SELECT $childColumns FROM $childTable c
                      INNER JOIN $tableName p ON p.id = c.parent_id
                      WHERE p.name = LOWER({name}) ${enabled(onlyEnabled, "p")}
                      ${searchField("c.name")}
                      ${order(Some(orderColumn), orderDirection, "c")}
                      LIMIT ${sqlLimit(limit)} OFFSET {offset}"""
      SQL(query).on('ss -> search(searchString),
        'name -> ParameterValue.toParameterValue(name),
        'offset -> offset)
        .as(childParser.*)
    }
  }

  /**
    * Gets the total number of children for the parent
    *
    * @param onlyEnabled If set to true will only count the children that are enabled
    * @param id The id for the parent
    * @return A integer value representing the total number of children
    */
  def getTotalChildren(onlyEnabled:Boolean=false, searchString:String="")(implicit id:Key, c:Connection=null) : Int = {
    withMRConnection { implicit c =>
      val query =
        s"""SELECT COUNT(*) as TotalChildren FROM $childTable
           |WHERE parent_id = {id} ${searchField("name")}
           |${enabled(onlyEnabled)}""".stripMargin
      SQL(query).on(
        'id -> ParameterValue.toParameterValue(id)(p = keyToStatement),
        'ss -> search(searchString)
      ).as(SqlParser.int("TotalChildren").single)
    }
  }
}
