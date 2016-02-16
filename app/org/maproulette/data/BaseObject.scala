package org.maproulette.data

/**
  * @author cuthbertm
  */
trait BaseObject[Key] {
  def name:String
  def id:Key
}
