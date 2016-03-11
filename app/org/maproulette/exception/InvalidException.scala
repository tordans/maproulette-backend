package org.maproulette.exception

/**
  * Simple exception class extending exception, to handle invalid API calls. This allows us to pattern
  * match on the exception and if InvalidException is found we return a BadRequest instead of
  * an InternalServerError
  *
  * @author cuthbertm
  */
class InvalidException(message:String) extends Exception(message)
