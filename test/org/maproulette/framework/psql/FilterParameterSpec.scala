package org.maproulette.framework.psql

import java.sql.SQLException

import anorm.NamedParameter
import org.joda.time.{DateTime, Months}
import org.maproulette.framework.psql.filter._
import org.scalatestplus.play.PlaySpec

/**
  * @author mcuthbert
  */
class FilterParameterSpec extends PlaySpec {
  val KEY   = "KEY"
  val VALUE = "VALUE"

  "BaseFilterParameter" should {
    "set correctly" in {
      val filter = BaseFilterParameter(KEY, VALUE)
      filter.sql() mustEqual s"$KEY = {$KEY}"
      val params = filter.parameters()
      params.size mustEqual 1
      params.head mustEqual NamedParameter(s"$KEY", VALUE)
    }

    "set operator correctly" in {
      BaseFilterParameter(KEY, VALUE, FilterOperator.GT).sql() mustEqual s"$KEY > {$KEY}"
    }

    "set negation correctly" in {
      BaseFilterParameter(KEY, VALUE, negate = true).sql() mustEqual s"NOT $KEY = {$KEY}"
    }

    "set value directly correctly" in {
      BaseFilterParameter(KEY, s"'$VALUE'", useValueDirectly = true)
        .sql() mustEqual s"$KEY = '$VALUE'"
    }

    "fail on invalid provided column" in {
      intercept[SQLException] {
        BaseFilterParameter(s"&$KEY", VALUE).sql()
      }
    }
  }

  "ConditionalFilter" should {
    "set correctly if condition true" in {
      val filter = FilterParameter.conditional(KEY, VALUE, includeOnlyIfTrue = true)
      filter.sql() mustEqual s"$KEY = {$KEY}"
      val params = filter.parameters()
      params.size mustEqual 1
      params.head mustEqual NamedParameter(s"$KEY", VALUE)
    }

    "not set if condition is false" in {
      FilterParameter.conditional(KEY, VALUE).sql() mustEqual ""
    }

    "set operator correctly" in {
      FilterParameter
        .conditional(KEY, VALUE, FilterOperator.LT, includeOnlyIfTrue = true)
        .sql() mustEqual s"$KEY < {$KEY}"
    }

    "set negation correctly" in {
      FilterParameter
        .conditional(KEY, VALUE, negate = true, includeOnlyIfTrue = true)
        .sql() mustEqual s"NOT $KEY = {$KEY}"
    }

    "set value directly correctly" in {
      FilterParameter
        .conditional(KEY, VALUE, useValueDirectly = true, includeOnlyIfTrue = true)
        .sql() mustEqual s"$KEY = $VALUE"
    }

    "fail on invalid provided column" in {
      intercept[SQLException] {
        FilterParameter.conditional("$%$KEY", VALUE, includeOnlyIfTrue = true).sql()
      }
    }
  }

  "DateFilterParameter" should {
    "set single start date correctly" in {
      val startDate = DateTime.now()
      val filter    = DateFilterParameter(KEY, startDate, null)
      filter.sql() mustEqual s"$KEY = {$KEY}"
      val params = filter.parameters()
      params.size mustEqual 1
      params.head mustEqual FilterParameter.buildNamedParameter(s"$KEY", startDate)
    }

    "set two dates correctly" in {
      val endDate   = DateTime.now()
      val startDate = endDate.minus(Months.ONE)
      val filter    = DateFilterParameter(KEY, startDate, endDate, FilterOperator.BETWEEN)
      filter.sql() mustEqual s"$KEY::DATE BETWEEN {${KEY}_date1} AND {${KEY}_date2}"
      val params = filter.parameters()
      params.size mustEqual 2
      params.head mustEqual FilterParameter.buildNamedParameter(s"${KEY}_date1", startDate)
      params.tail.head mustEqual FilterParameter.buildNamedParameter(s"${KEY}_date2", endDate)
    }

    "set negate correctly on two dates" in {
      DateFilterParameter(
        KEY,
        DateTime.now(),
        DateTime.now(),
        FilterOperator.BETWEEN,
        negate = true
      ).sql() mustEqual s"$KEY::DATE NOT BETWEEN {${KEY}_date1} AND {${KEY}_date2}"
    }

    "fail on invalid provided column" in {
      intercept[SQLException] {
        DateFilterParameter(s"432%$KEY", DateTime.now(), null).sql()
      }
    }
  }

  "SubQueryFilter" should {
    "Set standard subquery filter" in {
      val filter = SubQueryFilter(
        KEY,
        Query.simple(List(BaseFilterParameter("Key2", "value2")), "SELECT * FROM table")
      )
      filter.sql() mustEqual s"$KEY IN (SELECT * FROM table WHERE Key2 = {${Query.SECONDARY_QUERY_KEY}Key2})"
      val params = filter.parameters()
      params.size mustEqual 1
      params.head mustEqual FilterParameter.buildNamedParameter(s"${Query.SECONDARY_QUERY_KEY}Key2", "value2")
    }

    "Set standard subquery filter with two parameters" in {
      val filter = SubQueryFilter(
        KEY,
        Query.simple(
          List(
            BaseFilterParameter("key2", "value2"),
            BaseFilterParameter("key3", "value3", FilterOperator.LTE)
          ),
          "SELECT * FROM table",
          OR()
        )
      )
      filter.sql() mustEqual s"$KEY IN (SELECT * FROM table WHERE key2 = {${Query.SECONDARY_QUERY_KEY}key2} OR key3 <= {${Query.SECONDARY_QUERY_KEY}key3})"
      val params = filter.parameters()
      params.size mustEqual 2
      params.head mustEqual FilterParameter.buildNamedParameter(s"${Query.SECONDARY_QUERY_KEY}key2", "value2")
      params.tail.head mustEqual FilterParameter.buildNamedParameter(s"${Query.SECONDARY_QUERY_KEY}key3", "value3")
    }

    "Set exists subquery filter" in {
      val filter = SubQueryFilter(
        KEY,
        Query.simple(List(BaseFilterParameter("key2", "value2")), "SELECT * FROM table"),
        operator = FilterOperator.EXISTS
      )
      filter.sql() mustEqual s"EXISTS (SELECT * FROM table WHERE key2 = {${Query.SECONDARY_QUERY_KEY}key2})"
    }

    "Set equals subquery filter" in {
      val filter = SubQueryFilter(
        KEY,
        Query.simple(List(BaseFilterParameter("key2", "value2")), "SELECT * FROM table"),
        operator = FilterOperator.EQ
      )
      filter.sql() mustEqual s"$KEY = (SELECT * FROM table WHERE key2 = {${Query.SECONDARY_QUERY_KEY}key2})"
    }

    "set custom parameterKey correctly" in {
      val filter = SubQueryFilter(KEY, Query.simple(List(BaseFilterParameter(KEY, VALUE)), "SELECT * FROM table"))
      filter.sql()("custom") mustEqual s"$KEY IN (SELECT * FROM table WHERE KEY = {custom$KEY})"
    }

    "fail on invalid provided column" in {
      intercept[SQLException] {
        SubQueryFilter(
          KEY,
          Query.simple(List(BaseFilterParameter("$%Key2", "value2")), "SELECT * FROM table")
        ).sql()
      }
    }
  }

  "CustomFilterParameter" should {
    "set anything that is provided in the value" in {
      val filter = CustomFilterParameter("THIS IS A TEST")
      filter.sql() mustEqual "THIS IS A TEST"
      filter.parameters().size mustEqual 0
    }
  }

  "FuzzySearchFilterParameter" should {
    "sets all the correct values for fuzzy searches" in {
      val filter = FuzzySearchFilterParameter(KEY, VALUE)
      filter.sql() mustEqual FilterParameterSpec.DEFAULT_FUZZY_SQL(KEY)
      val params = filter.parameters()
      params.size mustEqual 1
      params.head mustEqual FilterParameter.buildNamedParameter(KEY, VALUE)
    }

    "sets fuzzy search with default levenshstein score if less than 1" in {
      val filter = FuzzySearchFilterParameter(KEY, VALUE, 0)
      filter.sql() mustEqual FilterParameterSpec.DEFAULT_FUZZY_SQL(KEY)
    }

    "sets fuzzy search with custom levenshstein score" in {
      val filter = FuzzySearchFilterParameter(KEY, VALUE, 56)
      filter.sql() mustEqual FilterParameterSpec.DEFAULT_FUZZY_SQL(KEY, score = 56)
    }

    "sets fuzzy search with custom metaphone size" in {
      val filter = FuzzySearchFilterParameter(KEY, VALUE, metaphoneSize = 67)
      filter.sql() mustEqual FilterParameterSpec.DEFAULT_FUZZY_SQL(KEY, size = 67)
    }

    "fail on invalid provided column" in {
      intercept[SQLException] {
        FuzzySearchFilterParameter(s"&$KEY", VALUE).sql()
      }
    }
  }
}

object FilterParameterSpec {
  def DEFAULT_FUZZY_SQL(
      key: String,
      parameterKey:String = Query.PRIMARY_QUERY_KEY,
      score: Int = FilterParameter.DEFAULT_LEVENSHSTEIN_SCORE,
      size: Int = FilterParameter.DEFAULT_METAPHONE_SIZE
  ) = s"""($key <> '' AND
      (LEVENSHTEIN(LOWER($key), LOWER({$key})) < $score OR
      METAPHONE(LOWER($key), 4) = METAPHONE(LOWER({$key}), $size) OR
      SOUNDEX(LOWER($key)) = SOUNDEX(LOWER({$key})))
      )"""
}
