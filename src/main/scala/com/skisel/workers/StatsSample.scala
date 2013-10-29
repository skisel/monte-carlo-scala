package com.skisel.workers

import language.postfixOps
import scala.collection.{mutable, immutable}
import scala.concurrent.forkjoin.ThreadLocalRandom
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory
import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus
import akka.cluster.Member
import akka.contrib.pattern.ClusterSingletonManager
import akka.routing.FromConfig
import akka.cluster.ClusterEvent.MemberRemoved
import MasterWorkerProtocol._
import scala.Some
import akka.actor.RootActorPath
import akka.cluster.ClusterEvent.UnreachableMember
import akka.cluster.ClusterEvent.MemberUp
import com.skisel.workers.MasterWorkerProtocol.NotifyLeader
import akka.cluster.ClusterEvent.CurrentClusterState
import scala.Tuple2

class CalculationService extends Actor with ActorLogging {
  context.actorOf(Props(classOf[StatsWorker]).withRouter(FromConfig), name = "workerRouter")

  val workers = mutable.Map.empty[ActorRef, Option[Tuple2[ActorRef, Any]]]
  val workQueue = mutable.Queue.empty[Tuple2[ActorRef, Any]]

  def notifyWorkers(): Unit = {
    if (!workQueue.isEmpty) {
      workers.foreach {
        case (worker, m) if m.isEmpty => worker ! WorkIsReady
        case _ =>
      }
    }
  }

  def receive = {
    case WorkerCreated(worker) =>
      log.info("Worker created: {}", worker)
      context.watch(worker)
      workers += (worker -> None)
      notifyWorkers()

    case WorkerRequestsWork(worker) =>
      log.info("Worker requests work: {}", worker)
      if (workers.contains(worker)) {
        if (workQueue.isEmpty)
          worker ! NoWorkToBeDone
        else if (workers(worker) == None) {
          val (workSender, work) = workQueue.dequeue()
          workers += (worker -> Some(workSender -> work))
          worker.tell(WorkToBeDone(work), workSender)
        }
      }

    case WorkIsDone(worker) =>
      if (!workers.contains(worker))
        log.error("Blurgh! {} said it's done work but we didn't know about him", worker)
      else
        workers += (worker -> None)

    case Terminated(worker) =>
      if (workers.contains(worker) && workers(worker) != None) {
        log.error("Blurgh! {} died while processing {}", worker, workers(worker))
        val (workSender, work) = workers(worker).get
        self.tell(work, workSender)
      }
      workers -= worker

    case CalculationJob(text) if text != "" ⇒
      log.info("Got job to process: {}", text)
      val words = text.split(" ")
      val replyTo = sender
      val aggregator = context.actorOf(Props(classOf[StatsAggregator], words.size, replyTo))
      words foreach {
        word ⇒
          workQueue.enqueue(aggregator -> word)
      }
      notifyWorkers()
  }
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

class StatsWorker extends Worker {
  var cache = Map.empty[String, Int]

  def doWork(workSender: ActorRef, work: Any): Unit = {
    work match {
      case work: String ⇒
        val length = cache.get(work) match {
          case Some(x) ⇒ x
          case None ⇒
            val x = work.length
            cache += (work -> x)
            x
        }
        workSender ! length
        self ! CalculationCompleted("Done")
    }
  }
}

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
    case job: CalculationJob if membersByAge.isEmpty ⇒
      sender ! CalculationFailed("Service unavailable, try again later")
    case job: CalculationJob ⇒
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
    context.actorSelection(RootActorPath(membersByAge.head.address) / "user" / "singleton" / "calculationService")

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
      singletonProps = _ ⇒ Props[CalculationService], singletonName = "calculationService",
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
      service ! CalculationJob("this is the text")
      service ! CalculationJob("apple hello")
      service ! CalculationJob("good morning")
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