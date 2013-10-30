package com.skisel.workers

import language.postfixOps
import scala.concurrent.forkjoin.ThreadLocalRandom
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory
import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus
import akka.contrib.pattern.ClusterSingletonManager
import com.skisel.cluster._
import LeaderNodeProtocol._
import scala.Some
import akka.actor.RootActorPath
import akka.cluster.ClusterEvent.UnreachableMember
import akka.cluster.ClusterEvent.MemberUp
import com.skisel.cluster.Leader
import akka.cluster.ClusterEvent.CurrentClusterState
import scala.reflect.classTag
import com.skisel.workers.StatsProtocol.{WordsWork, CalculationJob}

object StatsProtocol {
  case class CalculationJob(text: String) extends JobTrigger {
    def toWorkUnits: List[WorkUnit] = {
      text.split(" ").map(new WordsWork(_)).toList
    }
  }

  case class WordsWork(word: String) extends WorkUnit
}

class StatsAggregator(expectedResults: Int, replyTo: ActorRef) extends Actor {
  var results = IndexedSeq.empty[Int]
  context.setReceiveTimeout(3 seconds)

  def receive = {
    case wordCount: Int ⇒
      results = results :+ wordCount
      if (results.size == expectedResults) {
        val meanWordLength = results.sum.toDouble / results.size
        replyTo ! CalculationResult(meanWordLength)
        context.stop(self)
      }
    case ReceiveTimeout ⇒
      replyTo ! CalculationFailed("Service unavailable, try again later")
      context.stop(self)
  }
}

class StatsProcessor(actorRef: ActorRef) extends Actor {
  var cache = Map.empty[String, Int]

  def receive = {
    case work: WordsWork ⇒
      val length = cache.get(work.word) match {
        case Some(x) ⇒ x
        case None ⇒
          val x = work.word.length
          cache += (work.word -> x)
          x
      }
      sender ! length
      actorRef ! JobCompleted
  }
}



object StatsSampleOneMaster {
  def main(args: Array[String]): Unit = {
    val config =
      (if (args.nonEmpty) ConfigFactory.parseString(s"akka.remote.netty.tcp.port=${args(0)}")
      else ConfigFactory.empty).withFallback(
        ConfigFactory.parseString("akka.cluster.roles = [compute]")).
        withFallback(ConfigFactory.load())

    val system = ActorSystem("ClusterSystem", config)

    system.actorOf(ClusterSingletonManager.props(
      singletonProps = _ ⇒ Props(classOf[Leader[StatsProcessor]],classTag[StatsProcessor]), singletonName = "leader",
      terminationMessage = PoisonPill, role = Some("compute")),
      name = "singleton")
    system.actorOf(Props[Facade], name = "facade")
  }
}

object StatsSampleOneMasterClient {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("ClusterSystem")
    system.actorOf(Props(classOf[ClusterClient]), "client")
  }
}

class ClusterClient extends Actor {
  val cluster = Cluster(context.system)
  val servicePathElements = "/user/facade" match {
    case RelativeActorPath(elements) ⇒ elements
  }

  import context.dispatcher

  val tickTask = context.system.scheduler.scheduleOnce(10 seconds, self, "tick")

  var nodes = Set.empty[Address]

  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberEvent])
    cluster.subscribe(self, classOf[UnreachableMember])
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    tickTask.cancel()
  }

  def receive = {
    case "tick" if nodes.nonEmpty ⇒
      // just pick any one
      val address = nodes.toIndexedSeq(ThreadLocalRandom.current.nextInt(nodes.size))
      val service = context.actorSelection(RootActorPath(address) / servicePathElements)
      val job: CalculationJob = new CalculationJob("this is the text")
      val aggregator = context.actorOf(Props(classOf[StatsAggregator], job.toWorkUnits.size, self))
      service.tell(job,aggregator)
    case result: CalculationResult ⇒
      println(result)
    case failed: CalculationFailed ⇒
      println(failed)
    case state: CurrentClusterState ⇒
      nodes = state.members.collect {
        case m if m.hasRole("compute") && m.status == MemberStatus.Up ⇒ m.address
      }
    case MemberUp(m) if m.hasRole("compute") ⇒ nodes += m.address
    case other: MemberEvent ⇒ nodes -= other.member.address
    case UnreachableMember(m) ⇒ nodes -= m.address
  }

}