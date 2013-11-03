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
import akka.contrib.pattern.{DistributedPubSubExtension, DistributedPubSubMediator}
import DistributedPubSubMediator.Publish
import scala.concurrent.duration._
import com.skisel.cluster.FacadeProtocol.IAmTheLeader

class Leader[P >: Actor : ClassTag] extends Actor with ActorLogging {
  context.actorOf(Props(classOf[Node[P]], classTag[P]).withRouter(FromConfig()), "nodeRouter")
  val mediator = DistributedPubSubExtension(context.system).mediator
  val nodes = mutable.Map.empty[ActorRef, Option[Tuple2[ActorRef, Any]]]
  val workQueue = mutable.Queue.empty[Tuple2[ActorRef, WorkUnit]]

  import context.dispatcher

  val leaderPing = context.system.scheduler.schedule(1 seconds, 1 seconds, self, "tick")

  override def postStop(): Unit = {
    leaderPing.cancel()
  }

  def notifyNodes(): Unit = {
    if (!workQueue.isEmpty) {
      nodes.foreach {
        case (worker, m) if m.isEmpty => worker ! WorkIsReady
        case _ =>
      }
    }
  }

  def receive = {
    case "tick" =>
      mediator ! Publish("leader", IAmTheLeader)
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

    case jobMessage: CollectionJobMessage =>
      log.info("Got job to process: {}", jobMessage)
      val replyTo = sender
      jobMessage.workUnits.foreach {
        workUnit => workQueue.enqueue(replyTo -> workUnit)
      }
      notifyNodes()

    case item: ItemJobMessage =>
      log.info("Got job to process: {}", item)
      val replyTo = sender
      workQueue.enqueue(replyTo -> item.workUnit)
      notifyNodes()

  }
}
