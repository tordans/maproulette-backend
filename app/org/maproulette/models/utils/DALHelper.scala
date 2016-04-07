package org.maproulette.models.utils

import org.maproulette.models.dal.BaseDAL

/**
  * @author cuthbertm
  */
trait DALHelper {
  this:BaseDAL[_, _] =>

  private val ordinary = (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9') ++ Seq('_')).toSet

  def sqlLimit(value:Int) : String = if (value < 0) "ALL" else value + ""

  def enabled(value:Boolean, conjunction:String="AND") : String =
    if (value) s"$conjunction enabled = TRUE" else ""

  def search(value:String) : String = if (value.nonEmpty) s"%$value%" else "%"

  def order(orderColumn:Option[String]=None, orderDirection:String="ASC") : String = orderColumn match {
    case Some(column) =>
      val direction = orderDirection match {
        case "DESC" => "DESC"
        case _ => "ASC"
      }
      // sanitize the column name to prevent sql injection. Only allow underscores and A-Za-z
      if (column.forall(ordinary.contains(_))) {
        s"ORDER BY $column $direction"
      } else {
        ""
      }
    case None => ""
  }
}
