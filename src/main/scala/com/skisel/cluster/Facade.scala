package com.skisel.cluster

import language.postfixOps
import scala.collection.mutable
import akka.actor._

import akka.contrib.pattern.{DistributedPubSubMediator, DistributedPubSubExtension}
import akka.contrib.pattern.DistributedPubSubMediator.{SubscribeAck, Subscribe}
import com.skisel.cluster.LeaderNodeProtocol.IAmTheLeader
import akka.event.LoggingReceive


object FacadeProtocol {

  case class NotifyLeader(msg: Any)

  case class NotifyLeaderWhenAvailable(msg: Any)

}

//deployed to each cluster node
class Facade extends Actor with ActorLogging {

  import FacadeProtocol._

  val mediator = DistributedPubSubExtension(context.system).mediator
  val messageQueue = mutable.Queue.empty[Tuple2[ActorRef, Any]]
  var leader: Option[ActorRef] = None

  override def preStart(): Unit = {
    mediator ! Subscribe("leader", self)
  }

  def processTheQueue(): Unit = {
    leader match {
      case Some(sender) =>
        messageQueue.dequeueAll(_ => true).foreach {
          case (worker, m) =>
            sender.tell(m, worker)
            log.info("Dequeueing {} and message {}", worker, m)
          case _ =>
        }
      case None =>
    }
  }

  def receive = LoggingReceive {
    case notifyLeader: NotifyLeader if leader.isEmpty => //skips message
      log.info("Leader is not available, Message is not delivered: {}", notifyLeader.msg)
    case notifyLeader: NotifyLeader =>
      log.info("Notifying leader: {}", notifyLeader)
      leader.get.tell(notifyLeader.msg, sender)
    case notifyLeader: NotifyLeaderWhenAvailable if leader.isEmpty =>
      log.info("Got message while leader is not available {}. Will be sent when leader is back. ", notifyLeader)
      messageQueue.enqueue(sender -> notifyLeader.msg)
    case notifyLeader: NotifyLeaderWhenAvailable =>
      log.info("Notifying leader: {}", notifyLeader.msg)
      leader.get.tell(notifyLeader.msg, sender)
    case SubscribeAck(Subscribe("leader", `self`)) ⇒
      log.info("Subscribe acknowledged")
    case IAmTheLeader =>
      log.info("Leader is known {}", sender)
      leader = Some(sender)
      processTheQueue()
    case any: Any => log.info("got message: {}", any)
  }

}
