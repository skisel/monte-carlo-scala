package com.skisel.workers

import akka.actor.{Actor, ActorLogging, ActorRef}

abstract class Worker extends Actor with ActorLogging {
  import MasterWorkerProtocol._

  val facade = context.actorSelection("/user/facade")

  def leaderMsg(msg: Any) = NotifyLeader(msg)

  def doWork(workSender: ActorRef, work: Any): Unit

  override def preStart() = {
    facade ! leaderMsg(WorkerCreated(self))
  }

  def working(work: Any): Receive = {
    case WorkIsReady =>
    case NoWorkToBeDone =>
    case WorkToBeDone(_) =>
      log.error("Yikes. Master told me to do work, while I'm working.")
    case CalculationCompleted(result) =>
      log.info("Work is complete.  Result {}.", result)
      facade ! leaderMsg(WorkIsDone(self))
      facade ! leaderMsg(WorkerRequestsWork(self))
      context.become(idle)
  }

  def idle: Receive = {
    case WorkIsReady =>
      log.info("Requesting work")
      facade ! leaderMsg(WorkerRequestsWork(self))
    case WorkToBeDone(work) =>
      log.info("Got work {}", work)
      doWork(sender, work)
      context.become(working(work))
    case NoWorkToBeDone =>
  }

  def receive = idle
}