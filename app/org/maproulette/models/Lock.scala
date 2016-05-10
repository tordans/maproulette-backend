package org.maproulette.models

import org.joda.time.DateTime

/**
  * @author cuthbertm
  */
case class Lock(lockedTime:DateTime, itemType:Int, itemId:Long, userId:Long)

object Lock {
  def emptyLock = Lock(null, -1, -1, -1)
}
