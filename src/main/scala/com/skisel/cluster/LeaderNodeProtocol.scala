package com.skisel.cluster

import akka.actor.ActorRef


object LeaderNodeProtocol {

  case class WorkerCreated(worker: ActorRef)

  case class WorkerRequestsWork(worker: ActorRef)

  case class WorkIsDone(worker: ActorRef)

  case class WorkToBeDone(work: WorkUnit)

  case object WorkIsReady

  case object NoWorkToBeDone

  case object JobCompleted

  case object JobFailed

  trait WorkUnit

  trait JobTrigger {
    def toWorkUnits: List[WorkUnit]
  }


}