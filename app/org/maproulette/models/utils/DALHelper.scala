package org.maproulette.models.utils

import org.apache.commons.lang3.StringUtils
import org.maproulette.models.dal.BaseDAL

/**
  * @author cuthbertm
  */
trait DALHelper {
  this:BaseDAL[_, _] =>

  private val ordinary = (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9') ++ Seq('_')).toSet

  def sqlLimit(value:Int) : String = if (value < 0) "ALL" else value + ""

  def enabled(value:Boolean, tablePrefix:String="", conjunction:String="AND") : String = {
    val prefix = if (tablePrefix.nonEmpty) {
      tablePrefix + "."
    } else {
      ""
    }
    if (value) {
      s"$conjunction ${prefix}enabled = TRUE"
    } else {
      ""
    }
  }

  def searchField(column:String, conjunction:String="", key:String="ss") : String = s"$conjunction LOWER($column) LIKE LOWER({$key})"

  def search(value:String) : String = if (value.nonEmpty) s"%$value%" else "%"

  def order(orderColumn:Option[String]=None, orderDirection:String="ASC", tablePrefix:String="") : String = orderColumn match {
    case Some(column) =>
      val trueColumnName = if (tablePrefix.nonEmpty) {
        s"$tablePrefix.$column"
      } else {
        column
      }
      val direction = orderDirection match {
        case "DESC" => "DESC"
        case _ => "ASC"
      }
      // sanitize the column name to prevent sql injection. Only allow underscores and A-Za-z
      if (trueColumnName.forall(ordinary.contains(_))) {
        s"ORDER BY $trueColumnName $direction"
      } else {
        ""
      }
    case None => ""
  }

  def addExtraFilters(extra:String, conjunction:String="AND") : String =
    if (StringUtils.isNotEmpty(extra)) s"$conjunction ($extra)" else ""
}
