// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.models.utils

import anorm._
import org.apache.commons.lang3.StringUtils

import scala.collection.mutable.ListBuffer

/**
  * Helper functions for any Data Access Layer classes
  *
  * @author cuthbertm
  */
trait DALHelper {
  // The set of characters that are allowed for column names, so that we can sanitize in unknown input
  // for protection against SQL injection
  private val ordinary = (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9') ++ Seq('_')).toSet

  /**
    * Function will return "ALL" if value is 0 otherwise the value itself. Postgres does not allow
    * using 0 for ALL
    *
    * @param value The limit used in the query
    * @return ALL if 0 otherwise the value
    */
  def sqlLimit(value:Int) : String = if (value < 0) "ALL" else value + ""

  /**
    * All Map Roulette objects contain the enabled column that define whether it is enabled in the
    * system or not. This will create the WHERE part of the clause checking for enabled values in the
    * query
    *
    * @param value If looking only for enabled elements this needs to be set to true
    * @param tablePrefix If used as part of a join or simply the table alias if required
    * @param conjunction Defaulted to "AND", but can remove the conjunction or set to OR
    * @return
    */
  def enabled(value:Boolean, tablePrefix:String="", conjunction:String="AND") : String = {
    if (value) {
      s"$conjunction ${getPrefix(tablePrefix)}enabled = TRUE"
    } else {
      ""
    }
  }

  /**
    * Set the search field in the where clause correctly, it will also surround the values
    * with LOWER to make sure that match is case insensitive
    *
    * @param column The column that you are searching against
    * @param conjunction Default is AND, but can use AND or OR
    * @param key The search string that you are testing against
    * @return
    */
  def searchField(column:String, conjunction:String="AND", key:String="ss") : String =
    s"$conjunction LOWER($column) LIKE LOWER({$key})"

  /**
    * Corrects the search string by adding % before and after string, so that it doesn't rely
    * on simply an exact match. If value not supplied, then will simply return %
    *
    * @param value The search string that you are using to match with
    * @return
    */
  def search(value:String) : String = if (value.nonEmpty) s"%$value%" else "%"

  /**
    * Creates the ORDER functionality, with the column and direction
    *
    * @param orderColumn The column that you are ordering with (or multiple comma separated columns)
    * @param orderDirection Direction of ordering ASC or DESC
    * @param tablePrefix table alias if required
    * @return
    */
  def order(orderColumn:Option[String]=None, orderDirection:String="ASC", tablePrefix:String="") : String = orderColumn match {
    case Some(column) =>
      val direction = orderDirection match {
        case "DESC" => "DESC"
        case _ => "ASC"
      }
      // sanitize the column name to prevent sql injection. Only allow underscores and A-Za-z
      if (column.forall(this.ordinary.contains)) {
        s"ORDER BY ${getPrefix(tablePrefix)}$column $direction"
      } else {
        ""
      }
    case None => ""
  }

  def sqlWithParameters(query:String, parameters:ListBuffer[NamedParameter]) : SimpleSql[Row] = {
    if (parameters.nonEmpty) {
      SQL(query).on(parameters:_*)
    } else {
      SQL(query).asSimple[Row]()
    }
  }

  def parentFilter(parentId:Long, conjunction:String="AND") : String = if (parentId != -1) {
    s"$conjunction parent_id = $parentId"
  } else {
    ""
  }

  /**
    * Just appends the period at the end of the table prefix if the provided string is not empty
    *
    * @param prefix The table prefix that is being used in the query
    * @return
    */
  private def getPrefix(prefix:String) : String = if (StringUtils.isEmpty(prefix)) "" else s"$prefix."
}
