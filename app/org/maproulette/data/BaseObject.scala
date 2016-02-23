package org.maproulette.data

/**
  * @author cuthbertm
  */
trait BaseObject[Key] {
  def name:String
  def identifier:Option[String] = None
  def id:Key
}
