/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.psql

import anorm.NamedParameter
import org.maproulette.framework.psql.filter.SQLClause

import scala.util.Random

/**
  * Basic class that handles all the paging for a query
  *
  * @author mcuthbert
  */
case class Paging(limit: Int = 0, page: Int = 0) extends SQLClause {
  val randomPrefix: String = Random.nextInt(1000).toString

  override def sql()(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): String = {
    if (limit > 0) {
      s"LIMIT {${keyPrefix}limit} OFFSET {${keyPrefix}offset}"
    } else {
      ""
    }
  }

  private def keyPrefix(implicit parameterKey: String) = s"$parameterKey$randomPrefix"

  override def parameters()(
      implicit parameterKey: String = Query.PRIMARY_QUERY_KEY
  ): List[NamedParameter] = {
    if (limit > 0) {
      List(
        Symbol(s"${keyPrefix}limit")  -> limit,
        Symbol(s"${keyPrefix}offset") -> page * limit
      )
    } else {
      List.empty
    }
  }
}
