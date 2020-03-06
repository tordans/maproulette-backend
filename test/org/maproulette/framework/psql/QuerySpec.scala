package org.maproulette.framework.psql

import org.joda.time.DateTime
import org.maproulette.framework.psql.filter._
import org.scalatestplus.play.PlaySpec

/**
  * @author mcuthbert
  */
class QuerySpec extends PlaySpec {
  val KEY   = "KEY"
  val VALUE = "VALUE"

  "Query" should {
    "build standard query" in {
      val query = Query.simple(List(BaseFilterParameter(KEY, VALUE)), "SELECT * FROM table")
      query.sql() mustEqual "SELECT * FROM table WHERE KEY = {KEY}"
      val params = query.parameters()
      params.size mustEqual 1
      params.head mustEqual FilterParameter.buildNamedParameter(KEY, VALUE)
    }

    "build standard query with paging" in {
      val query = Query.simple(
        List(BaseFilterParameter(KEY, VALUE)),
        "SELECT * FROM table",
        paging = Paging(10, 2)
      )
      query.sql() mustEqual "SELECT * FROM table WHERE KEY = {KEY} LIMIT {limit} OFFSET {offset}"
      val params = query.parameters()
      params.size mustEqual 3
      params.head mustEqual FilterParameter.buildNamedParameter(KEY, VALUE)
      params(1) mustEqual FilterParameter.buildNamedParameter("limit", 10)
      params(2) mustEqual FilterParameter.buildNamedParameter("offset", 20)
    }

    "build standard query with ordering" in {
      val query = Query.simple(
        List(BaseFilterParameter(KEY, VALUE)),
        "SELECT * FROM table",
        order = Order.simple("test", Order.ASC)
      )
      query.sql() mustEqual "SELECT * FROM table WHERE KEY = {KEY} ORDER BY test ASC"
      query.parameters().size mustEqual 1
    }
  }

  "build standard query with grouping" in {
    val query = Query.simple(
      List(BaseFilterParameter(KEY, VALUE)),
      "SELECT * FROM table",
      grouping = Grouping("test1", "test2")
    )
    query.sql() mustEqual "SELECT * FROM table WHERE KEY = {KEY} GROUP BY test1,test2"
    query.parameters().size mustEqual 1
  }

  "build standard query with all extras" in {
    val query = Query.simple(
      List(BaseFilterParameter(KEY, VALUE)),
      "SELECT * FROM table",
      AND(),
      Paging(10, 5),
      Order.simple("order"),
      Grouping("test1")
    )
    query.sql() mustEqual "SELECT * FROM table WHERE KEY = {KEY} GROUP BY test1 ORDER BY order DESC LIMIT {limit} OFFSET {offset}"
    val params = query.parameters()
    params.size mustEqual 3
    params.head mustEqual FilterParameter.buildNamedParameter(KEY, VALUE)
    params(1) mustEqual FilterParameter.buildNamedParameter("limit", 10)
    params(2) mustEqual FilterParameter.buildNamedParameter("offset", 50)
  }

  "build complex query" in {
    val firstDate  = DateTime.now()
    val secondDate = DateTime.now()
    val query = Query(
      Filter(
        AND(),
        FilterGroup(OR(), BaseFilterParameter(KEY, VALUE), BaseFilterParameter("key2", "value2")),
        FilterGroup(
          OR(),
          CustomFilterParameter("a.g = g.a"),
          DateFilterParameter("dateField", firstDate, secondDate, FilterOperator.BETWEEN)
        ),
        FilterGroup(
          AND(),
          FuzzySearchFilterParameter("fuzzy", "fuzzyValue"),
          SubQueryFilter(
            "subQueryKey",
            Query.simple(
              List(BaseFilterParameter("subsubKey", "subsubValue")),
              "SELECT * FROM subTable",
              paging = Paging(10)
            )
          )
        )
      ),
      "SELECT * FROM table",
      paging = Paging(35, 15),
      order = Order.simple("oField")
    )

    query
      .sql() mustEqual s"SELECT * FROM table WHERE (KEY = {KEY} OR key2 = {key2}) AND (a.g = g.a OR dateField::DATE BETWEEN {dateField_date1} AND {dateField_date2}) AND (${FilterParameterSpec.DEFAULT_FUZZY_SQL("fuzzy")} AND subQueryKey IN (SELECT * FROM subTable WHERE subsubKey = {secondarysubsubKey} LIMIT {secondarylimit} OFFSET {secondaryoffset})) ORDER BY oField DESC LIMIT {limit} OFFSET {offset}"
    val params = query.parameters()
    params.size mustEqual 10
    params.head mustEqual FilterParameter.buildNamedParameter(KEY, VALUE)
    params(1) mustEqual FilterParameter.buildNamedParameter("key2", "value2")
    params(2) mustEqual FilterParameter.buildNamedParameter("dateField_date1", firstDate)
    params(3) mustEqual FilterParameter.buildNamedParameter("dateField_date2", secondDate)
    params(4) mustEqual FilterParameter.buildNamedParameter("fuzzy", "fuzzyValue")
    params(5) mustEqual FilterParameter.buildNamedParameter("secondarysubsubKey", "subsubValue")
    params(6) mustEqual FilterParameter.buildNamedParameter("secondarylimit", 10)
    params(7) mustEqual FilterParameter.buildNamedParameter("secondaryoffset", 0)
    params(8) mustEqual FilterParameter.buildNamedParameter("limit", 35)
    params(9) mustEqual FilterParameter.buildNamedParameter("offset", 525)
  }

  "handle input base query correctly when no base set" in {
    Query
      .simple(List(BaseFilterParameter(KEY, VALUE)))
      .sqlWithBaseQuery("SELECT * FROM projects") mustEqual s"SELECT * FROM projects WHERE $KEY = {$KEY}"
  }

  "handle input base query correctly when base is set" in {
    Query
      .simple(List(BaseFilterParameter(KEY, VALUE)), "SELECT * FROM projects")
      .sqlWithBaseQuery("SELECT * FROM challenges") mustEqual s"SELECT * FROM challenges WHERE $KEY = {$KEY}"
  }

  "handle input base query correctly when base is set and forced to base" in {
    Query
      .simple(List(BaseFilterParameter(KEY, VALUE)), "SELECT * FROM projects", forceBase = true)
      .sqlWithBaseQuery("SELECT * FROM challenges") mustEqual s"SELECT * FROM projects WHERE $KEY = {$KEY}"
  }
}
