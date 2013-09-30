package com.skisel.montecarlo

import language.postfixOps
import scala.concurrent.forkjoin.ThreadLocalRandom
import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus
import com.skisel.montecarlo.SimulationProtocol._
import akka.util.Timeout
import akka.cluster.ClusterEvent.MemberUp
import akka.actor.RootActorPath
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent.UnreachableMember


class ClusterAwareClient(servicePath: String) extends Actor {
  val cluster = Cluster(context.system)
  val servicePathElements = servicePath match {
    case RelativeActorPath(elements) ⇒ elements
    case _ ⇒ throw new IllegalArgumentException(
      "servicePath [%s] is not a valid relative actor path" format servicePath)
  }
  var nodes = Set.empty[Address]
  var requests = Set.empty[(ActorRef, Request)]

  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberEvent])
    cluster.subscribe(self, classOf[UnreachableMember])
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  def receive = {
    case req: Request if nodes.nonEmpty ⇒
      sendRequest(req, sender)
    case req: Request if nodes.isEmpty ⇒ {
      val tuple = (sender, req)
      requests = requests + tuple
    }
    case state: CurrentClusterState ⇒
      nodes = state.members.collect {
        case m if m.hasRole("compute") && m.status == MemberStatus.Up ⇒ m.address
      }
    case MemberUp(m) if m.hasRole("compute") ⇒ {
      nodes += m.address
      if (requests.nonEmpty) for (a <- requests) {
        requests -= a
        sendRequest(a._2, a._1)
      }
    }
    case other: MemberEvent ⇒ nodes -= other.member.address
    case UnreachableMember(m) ⇒ nodes -= m.address
  }


  def sendRequest(req: Request, actorToAnswer: ActorRef) {
    val address = nodes.toIndexedSeq(ThreadLocalRandom.current.nextInt(nodes.size))
    val service = context.actorSelection(RootActorPath(address) / servicePathElements)
    implicit val timeout = Timeout(1000)
    import context.dispatcher
    service.resolveOne() onSuccess {
      case ref: ActorRef => ref.tell(req,actorToAnswer)
    }
  }
}
