package com.skisel.cluster

import language.postfixOps
import scala.collection.mutable
import akka.actor._

import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator.{SubscribeAck, Subscribe}
import akka.event.LoggingReceive
import com.skisel.cluster.FacadeProtocol.{NotifyLeaderWhenAvailable, NotifyLeader}


trait FacadeConsumer {
  this: Actor ⇒
  private val facade = context.actorSelection("/user/facade")
  def leaderMsg(msg: Any) = facade ! NotifyLeader(msg)
  def leaderMsgLater(msg: Any) = facade ! NotifyLeaderWhenAvailable(msg)
  def leaderMsg(msg: Any, sender: ActorRef) = facade.tell(NotifyLeader(msg),sender)
  def leaderMsgLater(msg: Any, sender: ActorRef) = facade.tell(NotifyLeaderWhenAvailable(msg),sender)
}

object FacadeProtocol {

  case class NotifyLeader(msg: Any)

  case class NotifyLeaderWhenAvailable(msg: Any)

  case object IAmTheLeader

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
      (leader, sender) match {
        case (None, sender) =>
          log.info("Leader is known now {}", sender)
          leader = Some(sender)
          processTheQueue()
        case (Some(a), b) if a.path.equals(b.path) =>
          //do nothing - leader is same
        case (Some(a), sender) =>
          log.info("Leader has changed {}", sender)
          leader = Some(sender)
      }
    case any: Any => log.info("got message: {}", any)
  }

}
