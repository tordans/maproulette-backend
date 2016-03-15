package org.maproulette.exception

/**
  * NotFoundException should be throw whenever we try to retrieve an object based on the object id
  * and find nothing
  *
  * @author cuthbertm
  */
class NotFoundException(message:String) extends Exception(message)
