package org.maproulette.data.dal

import anorm._
import org.maproulette.data.BaseObject
import play.api.db.DB
import play.api.Play.current

/**
  * @author cuthbertm
  */
trait ParentDAL[Key, T<:BaseObject[Key], C<:BaseObject[Key]] extends BaseDAL[Key, T] {
  val childTable:String
  val childParser:RowParser[C]
  val childColumns:String = "*"

  def listChildren(limit:Int=10, offset:Int=0)(implicit id:Key) : List[C] = {
    // add a child caching option that will keep a list of children for the parent
    DB.withConnection { implicit c =>
      val sqlLimit = if (limit < 0) "ALL" else limit+""
      val query = s"SELECT $childColumns FROM $childTable WHERE parent_id = {id} LIMIT $sqlLimit OFFSET {offset}"
      SQL(query).on('id -> ParameterValue.toParameterValue(id)(p = keyToStatement), 'offset -> offset).as(childParser.*)
    }
  }
}
