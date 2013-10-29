package com.skisel.workers

import akka.actor.ActorRef

object MasterWorkerProtocol {
  // Messages from Workers
  case class WorkerCreated(worker: ActorRef)
  case class WorkerRequestsWork(worker: ActorRef)
  case class WorkIsDone(worker: ActorRef)

  // Messages to Workers
  case class NotifyLeader(msg: Any)
  case class WorkToBeDone(work: Any)
  case object WorkIsReady
  case object NoWorkToBeDone

  case class CalculationJob(text: String)
  case class CalculationResult(meanWordLength: Double)
  case class CalculationFailed(reason: String)
  case class CalculationCompleted(result: Any)
}