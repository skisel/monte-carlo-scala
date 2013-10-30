package com.skisel.cluster

import language.postfixOps
import scala.collection.mutable
import akka.actor._
import akka.routing.FromConfig
import LeaderNodeProtocol._
import scala.reflect._
import scala.Tuple2
import akka.actor.Terminated
import scala.Some

class Leader[P >: Actor: ClassTag] extends Actor with ActorLogging {
  context.actorOf(Props(classOf[Node[P]], classTag[P]).withRouter(FromConfig()), "nodeRouter")

  val nodes = mutable.Map.empty[ActorRef, Option[Tuple2[ActorRef, Any]]]
  val workQueue = mutable.Queue.empty[Tuple2[ActorRef, Any]]

  def notifyNodes(): Unit = {
    if (!workQueue.isEmpty) {
      nodes.foreach {
        case (worker, m) if m.isEmpty => worker ! WorkIsReady
        case _ =>
      }
    }
  }

  def receive = {
    case WorkerCreated(node) =>
      log.info("Node created: {}", node)
      context.watch(node)
      nodes += (node -> None)
      notifyNodes()

    case WorkerRequestsWork(node) =>
      log.info("Node requests work: {}", node)
      if (nodes.contains(node)) {
        if (workQueue.isEmpty)
          node ! NoWorkToBeDone
        else if (nodes(node) == None) {
          val (workSender, work) = workQueue.dequeue()
          nodes += (node -> Some(workSender -> work))
          node.tell(WorkToBeDone(work), workSender)
        }
      }

    case WorkIsDone(node) =>
      if (!nodes.contains(node))
        log.error("Blurgh! {} said it's done work but we didn't know about him", node)
      else
        nodes += (node -> None)

    case Terminated(node) =>
      if (nodes.contains(node) && nodes(node) != None) {
        log.error("Blurgh! {} died while processing {}", node, nodes(node))
        val (workSender, work) = nodes(node).get
        self.tell(work, workSender)
      }
      nodes -= node

    case trigger: JobTrigger =>
      log.info("Got job to process: {}", trigger)
      val replyTo = sender
      trigger.toWorkUnits.foreach {
        workUnit => workQueue.enqueue(replyTo -> workUnit)
      }
      notifyNodes()
  }
}
