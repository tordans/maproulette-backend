package org.maproulette.jobs

import javax.inject.{Inject, Named}
import akka.actor.{ActorRef, ActorSystem}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
  * @author cuthbertm
  */
class Scheduler @Inject() (val system: ActorSystem,
                           @Named("scheduler-actor") val schedulerActor:ActorRef,
                           @Named("challenge-scheduler-actor") val challengeSchedulerActor: ActorRef,
                           @Named("location-scheduler-actor") val locationSchedulerActor: ActorRef)
                          (implicit ec:ExecutionContext) {
  system.scheduler.schedule(1.minute, 1.hour, schedulerActor, "cleanLocks")
  system.scheduler.schedule(1.minute, 24.hour, challengeSchedulerActor, "runChallengeSchedules")
  system.scheduler.schedule(1.minute, 12.hour, locationSchedulerActor, "updateLocations")
}
