package org.maproulette.framework.psql.filter

import anorm.{NamedParameter, ToParameterValue}
import org.joda.time.DateTime
import org.maproulette.framework.psql.filter.FilterOperator.FilterOperator
import org.maproulette.framework.psql.{Query, SQLUtils}

/**
  * Filter Parameters are generally be used for filter select queries using some basic filtering. It
  * also can be used for updating objects, as the SET clause using a very similar structure to the
  * EQ operator.
  *
  * @author mcuthbert
  */
trait FilterParameter[T] extends SQLClause {
  val key: String
  val value: T
  val operator: FilterOperator  = FilterOperator.EQ
  val negate: Boolean           = false
  val useValueDirectly: Boolean = false

  def sql()(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): String = {
    if (operator == FilterOperator.CUSTOM) {
      SQLUtils.testColumnName(key)
      s"$negate$parameterKey$key$value"
    } else {
      val directValue = if (useValueDirectly) {
        // if we are using the value directly, then we must at least try to convert to a string
        Some(value.toString)
      } else {
        None
      }
      FilterOperator.format(key, operator, negate, directValue)
    }
  }

  def parameters()(
      implicit parameterKey: String = Query.PRIMARY_QUERY_KEY
  ): List[NamedParameter] = {
    if (operator == FilterOperator.CUSTOM || operator == FilterOperator.NULL || useValueDirectly) {
      List.empty
    } else {
      List(FilterParameter.buildNamedParameter(s"$parameterKey$key", value))
    }
  }
}

/**
  * Conditional Filter that will only apply the filter if a condition is met
  *
  * @param key The column to filter on
  * @param value The value to use in the column filter
  * @param operator The operator to use for the equation
  * @param negate Whether to negate the operation
  * @param useValueDirectly use the value directly in the operation, instead of using a NamedParameter
  * @tparam T
  */
case class BaseFilterParameter[T](
    override val key: String,
    override val value: T,
    override val operator: FilterOperator = FilterOperator.EQ,
    override val negate: Boolean = false,
    override val useValueDirectly: Boolean = false
) extends FilterParameter[T]

case class ConditionalFilterParameter[T](
    filter: FilterParameter[T],
    includeOnlyIfTrue: Boolean = false
) extends FilterParameter[T] {
  override val key: String = filter.key
  override val value: T    = filter.value

  override def sql()(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): String =
    if (includeOnlyIfTrue) {
      filter.sql()
    } else {
      ""
    }

  override def parameters()(
      implicit parameterKey: String = Query.PRIMARY_QUERY_KEY
  ): List[NamedParameter] =
    if (includeOnlyIfTrue) {
      filter.parameters()
    } else {
      List.empty
    }
}

case class DateFilterParameter(
    override val key: String,
    override val value: DateTime,
    value2: DateTime,
    override val operator: FilterOperator = FilterOperator.EQ,
    override val negate: Boolean = false
) extends FilterParameter[DateTime] {
  override def sql()(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): String = {
    if (operator == FilterOperator.BETWEEN) {
      SQLUtils.testColumnName(key)
      val negation = if (negate) {
        "NOT "
      } else {
        ""
      }
      s"$key::DATE ${negation}BETWEEN {$parameterKey${key}_date1} AND {$parameterKey${key}_date2}"
    } else {
      super.sql()
    }
  }

  override def parameters()(
      implicit parameterKey: String = Query.PRIMARY_QUERY_KEY
  ): List[NamedParameter] = {
    if (operator == FilterOperator.BETWEEN) {
      List(
        Symbol(s"$parameterKey${key}_date1") -> ToParameterValue
          .apply[DateTime](p = SQLUtils.toStatement)
          .apply(value),
        Symbol(s"$parameterKey${key}_date2") -> ToParameterValue
          .apply[DateTime](p = SQLUtils.toStatement)
          .apply(value2)
      )
    } else {
      super.parameters()
    }
  }
}

/**
  * Allows you to do something like 'column' IN ( subQuery ), need to add support for exists later
  *
  * @param key The column that you are checking against
  * @param value The sub query to check against, the subQuery needs a base query included
  */
case class SubQueryFilter(
    override val key: String,
    override val value: Query,
    override val negate: Boolean = false,
    override val operator: FilterOperator = FilterOperator.IN
) extends FilterParameter[Query] {
  override def sql()(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): String = {
    val filterValue = if (operator == FilterOperator.IN || operator == FilterOperator.EXISTS) {
      Some(value.sql()(this.getParameterKey))
    } else {
      Some(s"(${value.sql()(this.getParameterKey)})")
    }
    FilterOperator.format(key, operator, negate, filterValue)
  }

  override def parameters()(
      implicit parameterKey: String = Query.PRIMARY_QUERY_KEY
  ): List[NamedParameter] = value.parameters()(this.getParameterKey)

  // This may work for a single subquery, but there may be issues if you have more than one subquery
  // as it will always choose "SECONDARY" as it parameterKey which might not make the keys unique
  // for the parameter
  def getParameterKey(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): String = {
    if (parameterKey.equals(Query.PRIMARY_QUERY_KEY)) {
      Query.SECONDARY_QUERY_KEY
    } else {
      parameterKey
    }

  }
}

/**
  * Custom filter that will simply use the sql included in the filter
  *
  * @param key
  */
case class CustomFilterParameter(override val key: String, override val value: String = "")
    extends FilterParameter[String] {
  override def sql()(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): String = key

  override def parameters()(
      implicit parameterKey: String = Query.PRIMARY_QUERY_KEY
  ): List[NamedParameter] = List.empty
}

case class FuzzySearchFilterParameter(
    override val key: String,
    override val value: String,
    levenshsteinScore: Int = FilterParameter.DEFAULT_LEVENSHSTEIN_SCORE,
    metaphoneSize: Int = FilterParameter.DEFAULT_METAPHONE_SIZE
) extends FilterParameter[String] {
  override def sql()(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): String = {
    SQLUtils.testColumnName(key)
    val score = if (levenshsteinScore > 0) {
      levenshsteinScore
    } else {
      FilterParameter.DEFAULT_LEVENSHSTEIN_SCORE
    }
    s"""($key <> '' AND
      (LEVENSHTEIN(LOWER($key), LOWER({$parameterKey$key})) < $score OR
      METAPHONE(LOWER($key), 4) = METAPHONE(LOWER({$parameterKey$key}), $metaphoneSize) OR
      SOUNDEX(LOWER($key)) = SOUNDEX(LOWER({$parameterKey$key})))
      )"""
  }

  override def parameters()(
      implicit parameterKey: String = Query.PRIMARY_QUERY_KEY
  ): List[NamedParameter] =
    List(Symbol(s"$parameterKey${key}") -> value)
}

object FilterParameter {
  val DEFAULT_LEVENSHSTEIN_SCORE = 3
  val DEFAULT_METAPHONE_SIZE     = 4

  def buildNamedParameter[T](key: String, value: T): NamedParameter =
    Symbol(key) -> ToParameterValue.apply[T](p = SQLUtils.toStatement).apply(value)

  // Helper method to build a Conditional Filter
  def conditional[T](
      key: String,
      value: T,
      operator: FilterOperator = FilterOperator.EQ,
      negate: Boolean = false,
      useValueDirectly: Boolean = false,
      includeOnlyIfTrue: Boolean = false
  )(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): ConditionalFilterParameter[T] =
    ConditionalFilterParameter(
      BaseFilterParameter(key, value, operator, negate, useValueDirectly),
      includeOnlyIfTrue
    )
}
