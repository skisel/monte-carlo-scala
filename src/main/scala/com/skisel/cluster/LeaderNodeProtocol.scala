package com.skisel.cluster

import akka.actor.ActorRef

object LeaderNodeProtocol {

  case class WorkerCreated(worker: ActorRef)

  case class WorkerRequestsWork(worker: ActorRef)

  case class WorkIsDone(worker: ActorRef)

  // Messages to Workers
  case class NotifyLeader(msg: Any)

  case class NotifyLeaderWhenAvailable(msg: Any)

  case class WorkToBeDone(work: Any)

  case object WorkIsReady

  case object NoWorkToBeDone

  case class CalculationResult(meanWordLength: Double)

  case class CalculationFailed(reason: String)

  case object JobCompleted
  case object JobFailed

  trait WorkUnit

  trait JobTrigger {
    def toWorkUnits: List[WorkUnit]
  }


}