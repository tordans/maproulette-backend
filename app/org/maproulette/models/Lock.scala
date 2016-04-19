package org.maproulette.models

import org.joda.time.DateTime

/**
  * @author cuthbertm
  */
case class Lock(date:DateTime, itemType:Int, itemId:Long, userId:Long)
