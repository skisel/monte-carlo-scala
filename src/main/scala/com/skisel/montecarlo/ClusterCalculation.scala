package com.skisel.montecarlo

//#imports

import language.postfixOps
import scala.concurrent.forkjoin.ThreadLocalRandom
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory
import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Address
import akka.actor.Props
import akka.actor.RelativeActorPath
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus
import com.skisel.montecarlo.SimulationProtocol._
import akka.cluster.ClusterEvent.MemberUp
import akka.actor.RootActorPath
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent.UnreachableMember
import akka.util.Timeout
import scala.collection.JavaConverters._
import akka.pattern.ask

//#imports

//seed 2551
//seed 2552
//worker
//client

object Launcher {
  def main(args: Array[String]): Unit = {
    val arguments: List[String] = args.head.split(" ").toList
    arguments match {
      case "seed" :: Nil => println("please define port number")
      case "seed" :: tail => seed(tail.head)
      case "worker" :: Nil => worker()
      case "client" :: Nil => println("please define num of simulations")
      case "client" :: tail => client(tail.head.toInt)
      case _ => println("error")
    }
  }

  def seed(port: String) {
    val config =
      ConfigFactory.parseString(s"akka.remote.netty.tcp.port=${port}")
        .withFallback(ConfigFactory.parseString("akka.cluster.roles = [compute]"))
        .withFallback(ConfigFactory.parseString(s"atmos.trace.node = seed ${port}"))
        .withFallback(ConfigFactory.load())

    val system = ActorSystem("ClusterSystem", config)

    system.actorOf(Props[RunningActor], name = "statsWorker")
    system.actorOf(Props[PartitioningActor], name = "statsService")

  }

  def client(numOfSimulations: Int) {
    val config =
      ConfigFactory.empty
        .withFallback(ConfigFactory.parseString(s"atmos.trace.node = client"))
        .withFallback(ConfigFactory.load())

    val system = ActorSystem("ClusterSystem", config)
    system.actorOf(Props(classOf[ClusterCalculationClient], "/user/statsService"), "client")
  }

  def worker() {

    val config =
      ConfigFactory.empty
        .withFallback(ConfigFactory.parseString("akka.cluster.roles = [compute]"))
        .withFallback(ConfigFactory.parseString(s"atmos.trace.node = worker ${this.hashCode()}"))
        .withFallback(ConfigFactory.load())

    val system = ActorSystem("ClusterSystem", config)

    system.actorOf(Props[RunningActor], name = "statsWorker")
    system.actorOf(Props[PartitioningActor], name = "statsService")
  }
}

class ClusterCalculationClient(servicePath: String) extends Actor {
  val cluster = Cluster(context.system)
  val servicePathElements = servicePath match {
    case RelativeActorPath(elements) ⇒ elements
    case _ ⇒ throw new IllegalArgumentException(
      "servicePath [%s] is not a valid relative actor path" format servicePath)
  }

  var nodes = Set.empty[Address]

  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberEvent])
    cluster.subscribe(self, classOf[UnreachableMember])
    import context.dispatcher
    context.system.scheduler.scheduleOnce(10 seconds, self, "start")
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  def receive = {
    case "start" if nodes.nonEmpty ⇒
      import context.dispatcher
      val inp = new Input()
      val risks: List[Risk] = inp.getRisks.asScala.toList
      implicit val timeout = Timeout(3660000)
      val numOfSimulations: Int = 20000
      val address = nodes.toIndexedSeq(ThreadLocalRandom.current.nextInt(nodes.size))
      val service = context.actorSelection(RootActorPath(address) / servicePathElements)
      val results = service ask SimulateDealPortfolio(numOfSimulations, inp)
      //val results = runner ask LoadRequest(numOfSimulations)
      results.onSuccess {
        case responce: SimulationStatistics => {
          println(responce.reducedDistribution.mkString("\n"))
          println("hitting ratio:" + responce.hittingRatio)
          println("simulation loss:" + responce.simulationLoss)
          println("simulation loss reduced:" + responce.simulationLossReduced)
          println("analytical loss: " + risks.map(x => x.getPd * x.getValue).foldRight(0.0)(_ + _))
        }
      }
    case state: CurrentClusterState ⇒
      nodes = state.members.collect {
        case m if m.hasRole("compute") && m.status == MemberStatus.Up ⇒ m.address
      }
    case MemberUp(m) if m.hasRole("compute") ⇒ nodes += m.address
    case other: MemberEvent ⇒ nodes -= other.member.address
    case UnreachableMember(m) ⇒ nodes -= m.address
  }


}