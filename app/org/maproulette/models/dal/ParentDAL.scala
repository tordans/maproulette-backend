package org.maproulette.models.dal

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
  def listChildren(limit:Int=10, offset:Int=0, onlyEnabled:Boolean=false, searchString:String="")(implicit id:Key) : List[C] = {
    // add a child caching option that will keep a list of children for the parent
    db.withConnection { implicit c =>
      val query = s"""SELECT $childColumns FROM $childTable
                      WHERE parent_id = {id} ${enabled(onlyEnabled)}
                      AND name LIKE {ss}
                      LIMIT ${sqlLimit(limit)} OFFSET {offset}"""
      SQL(query).on('ss -> search(searchString),
                    'id -> ParameterValue.toParameterValue(id)(p = keyToStatement),
                    'offset -> offset)
        .as(childParser.*)
    }
  }
}
