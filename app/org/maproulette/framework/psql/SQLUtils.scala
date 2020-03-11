package org.maproulette.framework.psql

import java.sql.{PreparedStatement, SQLException}

import anorm.JodaParameterMetaData.JodaDateTimeMetaData
import anorm.{NamedParameter, ParameterValue, ToParameterValue, ToStatement}
import org.joda.time.DateTime

/**
  * A Basic utils class to handle any common functionality
  *
  * @author mcuthbert
  */
sealed trait SQLKey {
  def getSQLKey(): String
}

case class AND() extends SQLKey {
  override def getSQLKey(): String = "AND"
}

case class OR() extends SQLKey {
  override def getSQLKey(): String = "OR"
}

object SQLUtils {
  // The set of characters that are allowed for column names, so that we can sanitize in unknown input
  // for protection against SQL injection
  private val ordinary =
    (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9') ++ Seq('_') ++ Seq('.')).toSet

  def testColumnName(columnName: String): Unit = {
    if (!columnName.forall(this.ordinary.contains)) {
      throw new SQLException(s"Invalid column name provided `$columnName`")
    }
  }

  /**
    * Corrects the search string by adding % before and after string, so that it doesn't rely
    * on simply an exact match. If value not supplied, then will simply return %
    *
    * @param value The search string that you are using to match with
    * @return
    */
  def search(value: String): String = if (value.nonEmpty) s"%$value%" else "%"

  def toParameterValue[T](value: T): ParameterValue =
    ToParameterValue.apply[T](p = toStatement).apply(value)

  /**
    * Our key for our objects are current Long, but can support String if need be. This function
    * handles transforming java objects to SQL for a specific set related to the object key
    *
    * @tparam Key The type of Key, this is currently always Long, but could be changed easily enough in the future
    * @return
    */
  def toStatement[Key]: ToStatement[Key] = {
    new ToStatement[Key] {
      def set(s: PreparedStatement, i: Int, identifier: Key) =
        identifier match {
          case value: String       => ToStatement.stringToStatement.set(s, i, value)
          case Some(value: String) => ToStatement.stringToStatement.set(s, i, value)
          case value: Long         => ToStatement.longToStatement.set(s, i, value)
          case Some(value: Long)   => ToStatement.longToStatement.set(s, i, value)
          case value: Integer      => ToStatement.integerToStatement.set(s, i, value)
          case value: Boolean      => ToStatement.booleanToStatement.set(s, i, value)
          case value: DateTime     => ToStatement.jodaDateTimeToStatement.set(s, i, value)
          case value: List[_] =>
            value.head match {
              case _: Int =>
                ToStatement.listToStatement[Int].set(s, i, value.asInstanceOf[List[Int]])
              case _: Long =>
                ToStatement.listToStatement[Long].set(s, i, value.asInstanceOf[List[Long]])
              case _: String =>
                ToStatement.listToStatement[String].set(s, i, value.asInstanceOf[List[String]])
              case _ =>
                throw new UnsupportedOperationException(
                  "Unsupported list type provided. Only support Int, Long and String."
                )
            }
        }
    }
  }

  def buildNamedParameter[T](key: String, value: T): NamedParameter =
    Symbol(key) -> ToParameterValue.apply[T](p = SQLUtils.toStatement).apply(value)
}
