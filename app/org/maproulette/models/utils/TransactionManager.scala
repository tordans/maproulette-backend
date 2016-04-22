package org.maproulette.models.utils

import java.sql.Connection

import org.maproulette.models.dal.BaseDAL

/**
  * @author cuthbertm
  */
trait TransactionManager {
  this:BaseDAL[_, _] =>

  def withMRTransaction[T](block:Connection => T)(implicit conn:Connection=null): T = {
    conn match {
      case null => db.withTransaction { implicit c => block(c) }
      case c => block(c)
    }
  }
}
