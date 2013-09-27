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
import akka.routing.FromConfig
import com.skisel.montecarlo.SimulationProtocol._
import akka.cluster.ClusterEvent.MemberUp
import com.skisel.montecarlo.SimulationProtocol.AggregationResults
import com.skisel.montecarlo.SimulationProtocol.SimulatePortfolioRequest
import com.skisel.montecarlo.SimulationProtocol.LoadPortfolioRequest
import akka.actor.RootActorPath
import akka.cluster.ClusterEvent.CurrentClusterState
import com.skisel.montecarlo.SimulationProtocol.LoadRequest
import akka.cluster.ClusterEvent.UnreachableMember
import akka.util.Timeout
import scala.collection.JavaConverters._
import akka.pattern.ask

//#imports


class PartitioningActor extends Actor {

  val actor = context.actorOf(Props[RunningActor].withRouter(FromConfig), name = "workerRouter")
  private[this] var outstandingRequests = Map.empty[Int, Double]

  def partitions(numOfSimulation: Int): Iterator[IndexedSeq[Int]] = {
    (1 to numOfSimulation).grouped(1000)
  }

  def receive = {
    case simulationRequest: SimulationRequest => {
      for (part <- partitions(simulationRequest.numOfSimulations)) {
        actor ! SimulatePortfolioRequest(sender, part.head, part.last, simulationRequest)
      }
    }
    case loadRequest: LoadRequest => {
      for (part <- partitions(loadRequest.numOfSimulations)) {
        actor ! LoadPortfolioRequest(sender, part.head, loadRequest)
      }
    }

    case AggregationResults(eventIdToAmount, request: PortfolioRequest) => {
      for (tuple: (Int, Double) <- eventIdToAmount) {
        outstandingRequests += tuple._1 -> tuple._2
      }
      if (outstandingRequests.size == request.req.numOfSimulations) {
        val distribution: List[Double] = outstandingRequests.toList.map(_._2).sorted
        val simulationLoss: Double = distribution.foldRight(0.0)(_ + _) / request.req.numOfSimulations
        val dropTo = request.req.numOfSimulations / 1000
        val reducedDistribution: List[Double] = dropFunctional(dropTo, distribution)
        val reducedSimulationLoss: Double = reducedDistribution.foldRight(0.0)(_ + _) / 1000
        val hittingRatio: Double = distribution.count(_ > 0).toDouble / request.req.numOfSimulations.toDouble
        request.requestor ! SimulationStatistics(simulationLoss, reducedSimulationLoss, hittingRatio, reducedDistribution)
      }
    }

    case _ => println("Something failed PartitioningActor ")
  }

  def dropFunctional[A](n: Int, ls: List[A]): List[A] =
     ls.zipWithIndex filter {
       v => (v._2 + 1) % n == 0
     } map {
       _._1
     }

}

object ClusterCalculation {
  def main(args: Array[String]): Unit = {
    // Override the configuration of the port when specified as program argument
    val config =
      (if (args.nonEmpty) ConfigFactory.parseString(s"akka.remote.netty.tcp.port=${args(0)}")
      else ConfigFactory.empty).withFallback(
        ConfigFactory.parseString("akka.cluster.roles = [compute]")).
        withFallback(ConfigFactory.load())

    val system = ActorSystem("ClusterSystem", config)

    system.actorOf(Props[RunningActor], name = "statsWorker")
    system.actorOf(Props[PartitioningActor], name = "statsService")
  }
}

object ClusterCalculationClient {
  def main(args: Array[String]): Unit = {
    // note that client is not a compute node, role not defined
    val system = ActorSystem("ClusterSystem")
    system.actorOf(Props(classOf[ClusterCalculationClient], "/user/statsService"), "client")
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
          println("hitting ratio:"+responce.hittingRatio)
          println("simulation loss:"+responce.simulationLoss)
          println("simulation loss reduced:"+responce.simulationLossReduced)
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