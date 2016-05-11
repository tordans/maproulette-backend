package org.maproulette.models.utils

import java.sql.Connection

import play.api.db.Database

/**
  * @author cuthbertm
  */
trait TransactionManager {
  implicit val db:Database

  def withMRConnection[T](block:Connection => T)(implicit conn:Connection=null): T = {
    conn match {
      case null => db.withConnection { implicit c => block(c) }
      case c => block(c)
    }
  }

  def withMRTransaction[T](block:Connection => T)(implicit conn:Connection=null): T = {
    conn match {
      case null => db.withTransaction { implicit c => block(c) }
      case c => block(c)
    }
  }
}
