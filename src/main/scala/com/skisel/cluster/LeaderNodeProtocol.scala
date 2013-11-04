package com.skisel.cluster

import akka.actor.ActorRef


object LeaderNodeProtocol {

  case class WorkerCreated(worker: ActorRef)

  case class WorkerRequestsWork(worker: ActorRef)

  case class WorkIsDone(response: JobResponse, worker: ActorRef)

  case class WorkToBeDone(work: WorkUnit)

  case object WorkIsReady

  case object NoWorkToBeDone

  trait WorkUnit


  trait JobMessage

  trait CollectionJobMessage extends JobMessage {
    def workUnits: List[WorkUnit]
  }

  trait ItemJobMessage extends JobMessage {
    def workUnit: WorkUnit
  }
  trait JobTrigger extends JobMessage
  trait JobResponse extends JobMessage
  trait JobFailed extends JobResponse
  trait JobAcknowledged extends JobResponse
  trait JobCompleted extends JobResponse
}