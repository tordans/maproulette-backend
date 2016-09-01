// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.jobs

import javax.inject.{Inject, Named}
import akka.actor.{ActorRef, ActorSystem}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
  * @author cuthbertm
  */
class Scheduler @Inject() (val system: ActorSystem,
                           @Named("scheduler-actor") val schedulerActor:ActorRef)
                          (implicit ec:ExecutionContext) {
  this.system.scheduler.schedule(1.minute, 1.hour, this.schedulerActor, "cleanLocks")
  this.system.scheduler.schedule(1.minute, 24.hour, this.schedulerActor, "runChallengeSchedules")
  this.system.scheduler.schedule(1.minute, 12.hour, this.schedulerActor, "updateLocations")
}
