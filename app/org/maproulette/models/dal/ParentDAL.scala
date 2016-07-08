// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.models.dal

import java.sql.Connection

import anorm._
import org.maproulette.Config
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
  def listChildren(limit:Int=Config.DEFAULT_LIST_SIZE, offset:Int=0, onlyEnabled:Boolean=false, searchString:String="",
                   orderColumn:String="id", orderDirection:String="ASC")(implicit id:Key, c:Option[Connection]=None) : List[C] = {
    // add a child caching option that will keep a list of children for the parent
    this.withMRConnection { implicit c =>
      val query = s"""SELECT ${this.childColumns} FROM ${this.childTable}
                      WHERE parent_id = {id} ${this.enabled(onlyEnabled)}
                      ${this.searchField("name")}
                      ${this.order(Some(orderColumn), orderDirection)}
                      LIMIT ${this.sqlLimit(limit)} OFFSET {offset}"""
      SQL(query).on('ss -> this.search(searchString),
                    'id -> ParameterValue.toParameterValue(id)(p = keyToStatement),
                    'offset -> offset)
        .as(this.childParser.*)
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
  def listChildrenByName(limit:Int=Config.DEFAULT_LIST_SIZE, offset:Int=0, onlyEnabled:Boolean=false, searchString:String="",
                         orderColumn:String="id", orderDirection:String="ASC")(implicit name:String, c:Option[Connection]=None) : List[C] = {
    // add a child caching option that will keep a list of children for the parent
    this.withMRConnection { implicit c =>
      // TODO currently it will only check if the parent is enabled and not the child, this is because
      // there is the case where a Task is a child of challenge and so there is no enabled column on that table
      val query = s"""SELECT ${this.childColumns} FROM ${this.childTable} c
                      INNER JOIN ${this.tableName} p ON p.id = c.parent_id
                      WHERE p.name = LOWER({name}) ${this.enabled(onlyEnabled, "p")}
                      ${this.searchField("c.name")}
                      ${this.order(Some(orderColumn), orderDirection, "c")}
                      LIMIT ${this.sqlLimit(limit)} OFFSET {offset}"""
      SQL(query).on('ss -> this.search(searchString),
        'name -> ParameterValue.toParameterValue(name),
        'offset -> offset)
        .as(this.childParser.*)
    }
  }

  /**
    * Gets the total number of children for the parent
    *
    * @param onlyEnabled If set to true will only count the children that are enabled
    * @param id The id for the parent
    * @return A integer value representing the total number of children
    */
  def getTotalChildren(onlyEnabled:Boolean=false, searchString:String="")(implicit id:Key, c:Option[Connection]=None) : Int = {
    this.withMRConnection { implicit c =>
      val query =
        s"""SELECT COUNT(*) as TotalChildren FROM ${this.childTable}
           |WHERE parent_id = {id} ${this.searchField("name")}
           |${this.enabled(onlyEnabled)}""".stripMargin
      SQL(query).on(
        'id -> ParameterValue.toParameterValue(id)(p = keyToStatement),
        'ss -> this.search(searchString)
      ).as(SqlParser.int("TotalChildren").single)
    }
  }
}
