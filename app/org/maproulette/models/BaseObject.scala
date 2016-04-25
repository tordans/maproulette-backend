package org.maproulette.models

import org.maproulette.session.User

/**
  * Every object in the system uses this trait, with exception to the User object. This enables
  * a consistent workflow when used in caching, through the controllers and through the data access
  * layer. Simply it contains an id and name, with the id being typed but in this system it is pretty
  * much a long.
  *
  * @author cuthbertm
  */
trait BaseObject[Key] {
  def name:String
  def id:Key
  def description:Option[String] = None

  val itemType:Int

  /**
    * Whether a user has write access to an object or not.
    * By default it will assume that it does
    *
    * @param user The user to check
    * @return true if user can update the object
    */
  def hasWriteAccess(user:User) : Boolean = true
}

trait ChildObject[Key, P <: BaseObject[Key]] extends BaseObject[Key] {
  def parent:Key
  def getParent:P

  /**
    * Whether a user has write access to an object or not.
    * By default it will assume that it does
    *
    * @param user The user to check
    * @return true if user can update the object
    */
  override def hasWriteAccess(user: User): Boolean =
    user.isSuperUser || getParent.hasWriteAccess(user)
}

trait TagObject[Key] {
  this: BaseObject[Key] =>
  val tags:List[Tag]
}
