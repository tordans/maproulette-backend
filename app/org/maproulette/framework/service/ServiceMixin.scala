package org.maproulette.framework.service

import org.maproulette.framework.psql.Query

/**
  * @author mcuthbert
  */
trait ServiceMixin[T] {

  /**
    * Retrieves all the objects based on the search criteria
    *
    * @param query The query to match against to retrieve the objects
    * @return The list of objects
    */
  def query(query: Query): List[T]
}
