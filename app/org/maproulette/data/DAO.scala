package org.maproulette.data

import scala.reflect.runtime.universe._

/**
  * Maybe do this later. This would basically be a generic DAO object that I could then simply
  * applied to an object like Tag or Task and automatically gain CRUD operations. Don't need it
  * for prototype or initial version though.
  *
  * @author cuthbertm
  */
abstract class DAO[T:TypeTag] {
  implicit val table:String

  private val runtime = scala.reflect.runtime.universe

  def insert = ???

  def update = ???

  def delete = ???

  def read = ???
}
