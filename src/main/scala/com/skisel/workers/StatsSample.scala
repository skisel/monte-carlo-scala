package com.skisel.workers

import language.postfixOps
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory
import akka.actor._
import akka.contrib.pattern.ClusterSingletonManager
import com.skisel.cluster._
import LeaderNodeProtocol._
import scala.Some
import com.skisel.cluster.Leader
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
      singletonProps = _ ⇒ Props(classOf[Leader[StatsProcessor]], classTag[StatsProcessor]), singletonName = "leader",
      terminationMessage = PoisonPill, role = Some("compute")),
      name = "singleton")
    system.actorOf(Props[Facade], name = "facade")
  }
}

object StatsSampleOneMasterClient {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("ClusterSystem")
    system.actorOf(Props[Facade], name = "facade")
    system.actorOf(Props(classOf[ClusterClient]), "client")
  }
}

class ClusterClient extends Actor {
  val facade = context.actorSelection("/user/facade")

  override def preStart(): Unit = {
    val job: CalculationJob = new CalculationJob("this is the text")
    val aggregator = context.actorOf(Props(classOf[StatsAggregator], job.toWorkUnits.size, self))
    facade.tell(NotifyLeaderWhenAvailable(job), aggregator)
  }

  def receive = {
    case result: CalculationResult ⇒
      println(result)
    case failed: CalculationFailed ⇒
      println(failed)
  }
}