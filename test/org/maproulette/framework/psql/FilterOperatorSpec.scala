package org.maproulette.framework.psql

import org.maproulette.exception.InvalidException
import org.maproulette.framework.psql.filter.FilterOperator
import org.scalatestplus.play.PlaySpec

/**
  * @author mcuthbert
  */
class FilterOperatorSpec extends PlaySpec {
  private val KEY                   = "key"
  private implicit val parameterKey = "TestKey"

  "FilterOperator" should {
    "format EQ operator correctly" in {
      FilterOperator.format(KEY, FilterOperator.EQ) mustEqual s"$KEY = {$parameterKey$KEY}"
    }

    "format NOT EQ operator correctly" in {
      FilterOperator.format(KEY, FilterOperator.EQ, true) mustEqual s"NOT $KEY = {$parameterKey$KEY}"
    }

    "format GT operator correctly" in {
      FilterOperator.format(KEY, FilterOperator.GT) mustEqual s"$KEY > {$parameterKey$KEY}"
    }

    "format NOT GT operator correctly" in {
      FilterOperator.format(KEY, FilterOperator.GT, true) mustEqual s"NOT $KEY > {$parameterKey$KEY}"
    }

    "format GTE operator correctly" in {
      FilterOperator.format(KEY, FilterOperator.GTE) mustEqual s"$KEY >= {$parameterKey$KEY}"
    }

    "format NOT GTE operator correctly" in {
      FilterOperator.format(KEY, FilterOperator.GTE, true) mustEqual s"NOT $KEY >= {$parameterKey$KEY}"
    }

    "format LT operator correctly" in {
      FilterOperator.format(KEY, FilterOperator.LT) mustEqual s"$KEY < {$parameterKey$KEY}"
    }

    "format NOT LT operator correctly" in {
      FilterOperator.format(KEY, FilterOperator.LT, true) mustEqual s"NOT $KEY < {$parameterKey$KEY}"
    }

    "format LTE operator correctly" in {
      FilterOperator.format(KEY, FilterOperator.LTE) mustEqual s"$KEY <= {$parameterKey$KEY}"
    }

    "format NOT LTE operator correctly" in {
      FilterOperator.format(KEY, FilterOperator.LTE, negate = true) mustEqual s"NOT $KEY <= {$parameterKey$KEY}"
    }

    "format IN operator correctly" in {
      FilterOperator.format(KEY, FilterOperator.IN) mustEqual s"$KEY IN ({$parameterKey$KEY})"
    }

    "format NOT IN operator correctly" in {
      FilterOperator.format(KEY, FilterOperator.IN, negate = true) mustEqual s"NOT $KEY IN ({$parameterKey$KEY})"
    }

    "format LIKE operator correctly" in {
      FilterOperator.format(KEY, FilterOperator.LIKE) mustEqual s"$KEY LIKE {$parameterKey$KEY}"
    }

    "format NOT LIKE operator correctly" in {
      FilterOperator.format(KEY, FilterOperator.LIKE, negate = true) mustEqual s"NOT $KEY LIKE {$parameterKey$KEY}"
    }

    "format ILIKE operator correctly" in {
      FilterOperator.format(KEY, FilterOperator.ILIKE) mustEqual s"$KEY ILIKE {$parameterKey$KEY}"
    }

    "format NOT ILIKE operator correctly" in {
      FilterOperator.format(KEY, FilterOperator.ILIKE, negate = true) mustEqual s"NOT $KEY ILIKE {$parameterKey$KEY}"
    }

    "format SIMILAR TO operator correctly" in {
      FilterOperator.format(KEY, FilterOperator.SIMILAR_TO) mustEqual s"$KEY SIMILAR TO {$parameterKey$KEY}"
    }

    "format NOT SIMILAR TO operator correctly" in {
      FilterOperator.format(KEY, FilterOperator.SIMILAR_TO, negate = true) mustEqual s"NOT $KEY SIMILAR TO {$parameterKey$KEY}"
    }

    "format EXISTS operator correctly" in {
      FilterOperator.format(KEY, FilterOperator.EXISTS) mustEqual s"EXISTS ({$parameterKey$KEY})"
    }

    "format NOT EXISTS operator correctly" in {
      FilterOperator.format(KEY, FilterOperator.EXISTS, negate = true) mustEqual s"NOT EXISTS ({$parameterKey$KEY})"
    }

    "format rightValue correctly if set" in {
      FilterOperator.format(KEY, FilterOperator.EQ, value = Some("test.key")) mustEqual s"$KEY = test.key"
    }

    "between should throw invalidException" in {
      intercept[InvalidException] {
        FilterOperator.format(KEY, FilterOperator.BETWEEN)
      }
    }
  }
}
