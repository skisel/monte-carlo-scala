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
import com.skisel.instruments.metrics.MetricsReceiver

class Leader[P >: Actor : ClassTag] extends Actor with ActorLogging with MetricsReceiver {
  context.actorOf(Props(classOf[Node[P]], classTag[P]).withRouter(FromConfig()), "nodeRouter")
  val mediator = DistributedPubSubExtension(context.system).mediator
  val nodes = mutable.Map.empty[ActorRef, Option[(ActorRef, Any)]] //node -> requester -> work
  val workQueue = mutable.Queue.empty[(ActorRef, WorkUnit)]

  import context.dispatcher

  val leaderPing = context.system.scheduler.schedule(1 seconds, 1 seconds, self, "tick")

  override def preStart() = {
    log.info("Starting leader at {}", self.path.toStringWithAddress(self.path.address))
  }

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

  def wrappedReceive = {
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

    case WorkIsDone(msg, node) =>
      nodes.get(node) match {
        case Some(Some((requester, _))) =>
          requester ! msg
          nodes += (node -> None)
        case Some(None) =>
          log.error("Strange answer", node) //todo ???
        case None =>
          log.error("Blurgh! {} said it's done work but we didn't know about him", node)
      }

    case Terminated(node) =>
      if (nodes.contains(node) && nodes(node) != None) {
        log.error("Blurgh! {} died while processing {}", node, nodes(node))
        val (workSender, work) = nodes(node).get
        self.tell(work, workSender)
      }
      nodes -= node

    case jobMessage: CollectionJobMessage =>
      log.info("Got job to process: {}", jobMessage)
      jobMessage.workUnits.foreach {
        workUnit => workQueue.enqueue(sender -> workUnit)
      }
      notifyNodes()

    case item: ItemJobMessage =>
      log.info("Got job to process: {}", item)
      workQueue.enqueue(sender -> item.workUnit)
      notifyNodes()

  }
}
