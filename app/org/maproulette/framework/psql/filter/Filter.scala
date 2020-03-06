package org.maproulette.framework.psql.filter

import anorm.NamedParameter
import org.maproulette.framework.psql.{AND, Query, SQLKey}

/**
  * This class handles any filters that you want to add onto a query. Repositories only handle one
  * table, joins are special and would require special code and logic to handle correctly, however
  * joins are actively discouraged due to the complexity and the potential for large performance
  * costs.
  *
  * @author mcuthbert
  */
trait SQLClause {
  def sql()(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): String
  def parameters()(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): List[NamedParameter]
}

case class FilterGroup(key: SQLKey, params: FilterParameter[_]*) extends SQLClause {
  override def sql()(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): String =
    params.map(param => param.sql()).mkString(s" ${key.getSQLKey()} ")

  override def parameters()(
      implicit parameterKey: String = Query.PRIMARY_QUERY_KEY
  ): List[NamedParameter] =
    params.flatMap(param => param.parameters()).toList
}

case class Filter(key: SQLKey, groups: FilterGroup*) extends SQLClause {
  def sql()(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): String = {
    val groupSeparator = if (groups.size == 1) {
      ("", "")
    } else {
      ("(", ")")
    }
    groups
      .map(group => s"${groupSeparator._1}${group.sql()}${groupSeparator._2}")
      .mkString(s" ${key.getSQLKey()} ")
  }

  def parameters()(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): List[NamedParameter] =
    groups.flatMap(param => param.parameters()).toList
}

object Filter {
  def simple(parameters: List[FilterParameter[_]], key: SQLKey = AND()): Filter =
    Filter(key, FilterGroup(key, parameters: _*))
}
