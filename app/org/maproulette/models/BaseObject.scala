package org.maproulette.models

/**
  * @author cuthbertm
  */
trait BaseObject[Key] {
  def name:String
  def identifier:Option[String] = None
  def id:Key
}
