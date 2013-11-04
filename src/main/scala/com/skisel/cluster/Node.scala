package com.skisel.cluster

import akka.actor.{Props, Actor, ActorLogging}
import LeaderNodeProtocol._
import scala.reflect.{ClassTag, classTag}

//worker node
class Node[P <: Actor : ClassTag] extends Actor with ActorLogging with LeaderConsumer {
  def props: Props = Props(classTag[P].runtimeClass, self)

  override def preStart() = {
    leaderMsgLater(WorkerCreated(self))
  }

  def working(work: Any): Receive = {
    case WorkIsReady =>
    case NoWorkToBeDone =>
    case WorkToBeDone(_) =>
      log.error("Yikes. Master told me to do work, while I'm working.")
    case ca: JobCompleted =>
      log.info("Work is complete.")
      leaderMsg(WorkIsDone(ca, self))
      leaderMsg(WorkerRequestsWork(self))
      context.become(idle)
      context.stop(sender) //stop processor
    case ca: JobFailed =>
      log.info("Work failed.")
      leaderMsg(WorkIsDone(ca, self))
      leaderMsg(WorkerRequestsWork(self))
      context.become(idle)
      context.stop(sender) //stop processor
    case ca: JobAcknowledged =>
      log.info("Job acknowledged.")
      leaderMsg(ca)
  }

  def idle: Receive = {
    case WorkIsReady =>
      log.info("Requesting work")
      leaderMsg(WorkerRequestsWork(self))
    case WorkToBeDone(work) =>
      log.info("Got work {}", work)
      val processor = context.actorOf(props)
      processor ! work
      context.become(working(work))
    case NoWorkToBeDone =>
  }

  def receive = idle
}