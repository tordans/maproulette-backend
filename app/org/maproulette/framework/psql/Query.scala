package org.maproulette.framework.psql

import anorm._
import org.maproulette.framework.psql.filter.{Filter, FilterGroup, FilterParameter, SQLClause}
import org.slf4j.{Logger, LoggerFactory}

/**
  * Class that handles a basic PsqlQuery options that can be used to modify the base queries in the
  * Repository. This allows a user to make minor modifications without having to actually write SQL
  * queries.
  *
  * @author mcuthbert
  */
object Query {
  val logger: Logger      = LoggerFactory.getLogger(Query.getClass)
  val PRIMARY_QUERY_KEY   = ""
  val SECONDARY_QUERY_KEY = "secondary"

  //val config:Config
  def devMode(): Boolean = true //config.isDebugMode || config.isDevMode

  def simple(
      parameters: List[FilterParameter[_]],
      base: String = "",
      key: SQLKey = AND(),
      paging: Paging = Paging(),
      order: Order = Order(),
      grouping: Grouping = Grouping(),
      forceBase: Boolean = false
  ): Query =
    Query(Filter(key, FilterGroup(key, parameters: _*)), base, paging, order, grouping, forceBase)
}

case class Query(
    filter: Filter,
    base: String = "",
    paging: Paging = Paging(),
    order: Order = Order(),
    grouping: Grouping = Grouping(),
    forceBase: Boolean = false
) extends SQLClause {
  def build(
      baseQuery: String = ""
  )(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): SimpleSql[Row] = {
    val parameters = this.parameters()
    val sql        = this.sqlWithBaseQuery(baseQuery)
    if (Query.devMode()) {
      Query.logger.debug(sql)
    }
    if (parameters.nonEmpty) {
      SQL(sql).on(parameters: _*)
    } else {
      SQL(sql).asSimple[Row]()
    }
  }

  override def parameters()(
      implicit parameterKey: String = Query.PRIMARY_QUERY_KEY
  ): List[NamedParameter] = filter.parameters() ++ paging.parameters()

  def sqlWithBaseQuery(
      baseQuery: String = ""
  )(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): String = {
    val filterQuery = filter.sql() match {
      case x if x.nonEmpty => s"WHERE $x"
      case x               => x
    }
    val pagingQuery = paging.sql()
    val start = if (forceBase || baseQuery.isEmpty) {
      base
    } else {
      baseQuery
    }
    val query =
      s"$start${this.format(filterQuery)}${this.format(grouping.sql())}${this.format(order.sql())}${this
        .format(pagingQuery)}"
    query
  }

  // Simple function that makes sure I don't have a bunch of empty spaces at the end of the query.
  // It doesn't really matter, but makes it easier to look at.
  private def format(value: String): String =
    if (value.isEmpty) {
      ""
    } else {
      s" $value"
    }

  override def sql()(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): String =
    this.sqlWithBaseQuery()

  def baseEmpty(): Boolean = base.isEmpty
}
