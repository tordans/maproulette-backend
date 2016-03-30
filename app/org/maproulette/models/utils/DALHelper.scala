package org.maproulette.models.utils

import org.maproulette.models.dal.BaseDAL

/**
  * @author cuthbertm
  */
trait DALHelper {
  this:BaseDAL[_, _] =>

  def sqlLimit(value:Int) : String = if (value < 0) "ALL" else value + ""

  def enabled(value:Boolean, conjunction:String="AND") : String =
    if (value) s"$conjunction enabled = TRUE" else ""

  def search(value:String) : String = if (value.nonEmpty) s"%$value%" else "%"
}
