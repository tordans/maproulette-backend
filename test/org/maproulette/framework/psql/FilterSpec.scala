package org.maproulette.framework.psql

import org.maproulette.framework.psql.filter.{BaseFilterParameter, Filter, FilterGroup, FilterParameter}
import org.scalatestplus.play.PlaySpec

/**
  * @author mcuthbert
  */
class FilterSpec extends PlaySpec {
  val KEY    = "KEY"
  val VALUE  = "VALUE"
  val KEY2   = "KEY2"
  val VALUE2 = "VALUE2"

  "FilterGroup" should {
    "Create a group of AND filters parameters" in {
      val group =
        FilterGroup(AND(), BaseFilterParameter(KEY, VALUE), BaseFilterParameter(KEY2, VALUE2))
      group.sql() mustEqual "KEY = {KEY} AND KEY2 = {KEY2}"
      val params = group.parameters()
      params.size mustEqual 2
      params.head mustEqual FilterParameter.buildNamedParameter(KEY, VALUE)
      params.tail.head mustEqual FilterParameter.buildNamedParameter(KEY2, VALUE2)
    }

    "Create a group of OR filter parameters" in {
      val group =
        FilterGroup(OR(), BaseFilterParameter(KEY, VALUE), BaseFilterParameter(KEY2, VALUE2))
      group.sql() mustEqual "KEY = {KEY} OR KEY2 = {KEY2}"
      val params = group.parameters()
      params.size mustEqual 2
      params.head mustEqual FilterParameter.buildNamedParameter(KEY, VALUE)
      params.tail.head mustEqual FilterParameter.buildNamedParameter(KEY2, VALUE2)
    }
  }

  "Filter" should {
    "generate standard sql for AND parameters" in {
      val filter = Filter.simple(
        List(BaseFilterParameter(KEY, VALUE), BaseFilterParameter(KEY2, VALUE2)),
        AND()
      )
      filter.sql() mustEqual "KEY = {KEY} AND KEY2 = {KEY2}"
      val params = filter.parameters()
      params.size mustEqual 2
      params.head mustEqual FilterParameter.buildNamedParameter(KEY, VALUE)
      params.tail.head mustEqual FilterParameter.buildNamedParameter(KEY2, VALUE2)
    }

    "generate standard sql for OR parameters" in {
      val filter = Filter.simple(
        List(BaseFilterParameter(KEY, VALUE), BaseFilterParameter(KEY2, VALUE2)),
        OR()
      )
      filter.sql() mustEqual "KEY = {KEY} OR KEY2 = {KEY2}"
      val params = filter.parameters()
      params.size mustEqual 2
      params.head mustEqual FilterParameter.buildNamedParameter(KEY, VALUE)
      params.tail.head mustEqual FilterParameter.buildNamedParameter(KEY2, VALUE2)
    }

    "generate filter groups for AND parameters" in {
      val filter = this.genericFilter(AND(), AND(), AND())
      filter.sql() mustEqual "(KEY = {KEY} AND KEY_1 = {KEY_1}) AND (KEY2 = {KEY2} AND KEY2_2 = {KEY2_2})"
      val params = filter.parameters()
      params.size mustEqual 4
      params.head mustEqual FilterParameter.buildNamedParameter(KEY, VALUE)
      params(1) mustEqual FilterParameter.buildNamedParameter(s"${KEY}_1", VALUE)
      params(2) mustEqual FilterParameter.buildNamedParameter(KEY2, VALUE2)
      params(3) mustEqual FilterParameter.buildNamedParameter(s"${KEY2}_2", VALUE2)
    }

    "generate filter groups for OR parameters" in {
      val filter = this.genericFilter(OR(), OR(), OR())
      filter
        .sql() mustEqual "(KEY = {KEY} OR KEY_1 = {KEY_1}) OR (KEY2 = {KEY2} OR KEY2_2 = {KEY2_2})"
      val params = filter.parameters()
      params.size mustEqual 4
    }

    "generate filter groups for AND/OR/AND parameters" in {
      val filter = this.genericFilter(OR(), AND(), AND())
      filter.sql() mustEqual "(KEY = {KEY} AND KEY_1 = {KEY_1}) OR (KEY2 = {KEY2} AND KEY2_2 = {KEY2_2})"
      val params = filter.parameters()
      params.size mustEqual 4
    }

    "generate filter groups for OR/AND/OR parameters" in {
      val filter = this.genericFilter(AND(), OR(), OR())
      filter.sql() mustEqual "(KEY = {KEY} OR KEY_1 = {KEY_1}) AND (KEY2 = {KEY2} OR KEY2_2 = {KEY2_2})"
      val params = filter.parameters()
      params.size mustEqual 4
    }

    "generate filter groups for OR/OR/AND parameters" in {
      val filter = this.genericFilter(OR(), OR(), AND())
      filter.sql() mustEqual "(KEY = {KEY} OR KEY_1 = {KEY_1}) OR (KEY2 = {KEY2} AND KEY2_2 = {KEY2_2})"
      val params = filter.parameters()
      params.size mustEqual 4
    }
  }

  private def genericFilter(key: SQLKey, group1Key: SQLKey, group2Key: SQLKey): Filter = {
    Filter(
      key,
      FilterGroup(
        group1Key,
        BaseFilterParameter(KEY, VALUE),
        BaseFilterParameter(s"${KEY}_1", VALUE)
      ),
      FilterGroup(
        group2Key,
        BaseFilterParameter(KEY2, VALUE2),
        BaseFilterParameter(s"${KEY2}_2", VALUE2)
      )
    )
  }
}
