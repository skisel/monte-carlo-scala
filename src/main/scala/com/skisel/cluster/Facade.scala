package com.skisel.cluster

import language.postfixOps
import scala.collection.immutable
import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.Member
import LeaderNodeProtocol._
import akka.actor.RootActorPath

class Facade extends Actor with ActorLogging {

  val cluster = Cluster(context.system)
  // sort by age, oldest first
  val ageOrdering = Ordering.fromLessThan[Member] {
    (a, b) ⇒ a.isOlderThan(b)
  }
  var membersByAge: immutable.SortedSet[Member] = immutable.SortedSet.empty(ageOrdering)
  override def preStart(): Unit = cluster.subscribe(self, classOf[MemberEvent])
  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    case notifyLeader: NotifyLeader =>
      log.info("Notifying leader: {}", notifyLeader)
      currentLeader.tell(notifyLeader.msg, sender)
    case job: JobTrigger if membersByAge.isEmpty ⇒
      sender ! CalculationFailed("Service unavailable, try again later")
    case job: JobTrigger ⇒
      currentLeader.tell(job, sender)
    case state: CurrentClusterState ⇒
      membersByAge = immutable.SortedSet.empty(ageOrdering) ++ state.members.collect {
        case m if m.hasRole("compute") ⇒ m
      }
    case MemberUp(m) ⇒ if (m.hasRole("compute")) membersByAge += m
    case MemberRemoved(m, _) ⇒ if (m.hasRole("compute")) membersByAge -= m
    case _: MemberEvent ⇒ // not interesting
  }

  def currentLeader: ActorSelection =
    context.actorSelection(RootActorPath(membersByAge.head.address) / "user" / "singleton" / "leader")

}
