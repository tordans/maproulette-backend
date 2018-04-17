// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.models.utils

import java.sql.Connection

import play.api.db.Database

/**
  * @author cuthbertm
  */
trait TransactionManager {
  implicit val db:Database

  def withMRConnection[T](block:Connection => T)(implicit conn:Connection=null): T = if (conn == null) {
    this.db.withConnection { implicit c => block(c) }
  } else {
    block(conn)
  }

  def withMRTransaction[T](block:Connection => T)(implicit conn:Connection=null): T = if (conn == null) {
    this.db.withTransaction { implicit c => block(c) }
  } else {
    block(conn)
  }
}
