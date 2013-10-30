package com.skisel.cluster

import language.postfixOps
import scala.collection.{mutable, immutable}
import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.Member
import LeaderNodeProtocol._
import akka.actor.RootActorPath

//deployed to each cluster node
class Facade extends Actor with ActorLogging {

  val cluster = Cluster(context.system)
  // sort by age, oldest first
  val ageOrdering = Ordering.fromLessThan[Member] {
    (a, b) ⇒ a.isOlderThan(b)
  }
  var membersByAge: immutable.SortedSet[Member] = immutable.SortedSet.empty(ageOrdering)

  val messageQueue = mutable.Queue.empty[Tuple2[ActorRef, Any]]

  override def preStart(): Unit = cluster.subscribe(self, classOf[MemberEvent])

  override def postStop(): Unit = cluster.unsubscribe(self)

  def notifyLeader(): Unit = {
    if (!membersByAge.isEmpty) {
      messageQueue.dequeueAll(_ => true).foreach {
        case (worker, m) =>
          currentLeader.tell(m,worker)
          log.info("Dequeueing {} and message {}", worker, m)
        case _ =>
      }
    }
  }


  def receive = {
    //business events
    case notifyLeader: NotifyLeader if membersByAge.isEmpty => //skips message
      log.info("Leader is not available, Message is not delivered: {}", notifyLeader.msg)
    case notifyLeader: NotifyLeader =>
      log.info("Notifying leader: {}", notifyLeader)
      currentLeader.tell(notifyLeader.msg, sender)
    case notifyLeader: NotifyLeaderWhenAvailable if membersByAge.isEmpty =>
      log.info("Got message while leader is not available {}. Will be sent when leader is back. ", notifyLeader)
      messageQueue.enqueue(sender -> notifyLeader.msg)
    case notifyLeader: NotifyLeaderWhenAvailable =>
      log.info("Notifying leader: {}", notifyLeader.msg)
      currentLeader.tell(notifyLeader.msg, sender)
    //cluster events:
    case state: CurrentClusterState ⇒
      membersByAge = immutable.SortedSet.empty(ageOrdering) ++ state.members.collect {
        case m if m.hasRole("compute") ⇒ m
      }
      notifyLeader()
    case MemberUp(m) ⇒
      if (m.hasRole("compute")) {
        membersByAge += m
        notifyLeader()
      }
    case MemberRemoved(m, _) ⇒
      if (m.hasRole("compute")) membersByAge -= m
    case _: MemberEvent ⇒ // not interesting
  }

  def currentLeader: ActorSelection =
    context.actorSelection(RootActorPath(membersByAge.head.address) / "user" / "singleton" / "leader")

}
