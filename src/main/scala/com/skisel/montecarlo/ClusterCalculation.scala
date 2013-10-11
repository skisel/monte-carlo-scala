package com.skisel.montecarlo

import language.postfixOps
import com.typesafe.config.ConfigFactory
import akka.actor._
import akka.util.Timeout
import scala.collection.JavaConverters._
import com.skisel.montecarlo.SimulationProtocol.{LoadRequest, Request, SimulationStatistics, SimulateDealPortfolio}
import akka.pattern.ask
import com.skisel.montecarlo.entity.Risk
import scala.util.{Failure, Success}

//seed 2551
//seed 2552
//worker
//client

object Launcher {
  def main(args: Array[String]): Unit = {
    val arguments: List[String] = args.head.trim.split(" ").toList
    arguments match {
      case "seed" :: Nil => println("please define port number")
      case "seed" :: port :: Nil => seed(port, "localhost")
      case "seed" :: port :: tail => seed(port, tail.head)
      case "worker" :: Nil => worker()
      case "client" :: Nil => println("please define operation")
      case "client" :: "sim" :: Nil => println("please define number of simulations")
      case "client" :: "sim" :: tail => {
        val inp = new Input()
        println("analytical loss: " + inp.getRisks.asScala.toList.map(x => x.getPd * x.getValue).foldRight(0.0)(_ + _))
        callRun(SimulateDealPortfolio(tail.head.toInt, inp))
      }
      case "client" :: "load" :: Nil => println("please define calculation key")
      case "client" :: "load" :: tail => {
        val inp = new Input()
        println("analytical loss: " + inp.getRisks.asScala.toList.map(x => x.getPd * x.getValue).foldRight(0.0)(_ + _))
        callRun(LoadRequest("#" + tail.head))
      }
      case _ => println("error")
    }
  }

  def seed(port: String, host: String) {
    val config =
      ConfigFactory.parseString(s"akka.remote.netty.tcp.port=${port}")
        .withFallback(ConfigFactory.parseString("akka.cluster.roles = [compute]"))
        .withFallback(ConfigFactory.parseString(s"atmos.trace.node = seed ${port}"))
        .withFallback(ConfigFactory.load())

    val system = ActorSystem("ClusterSystem", config)

    system.actorOf(Props[RunningActor], name = "runningActor")
    system.actorOf(Props[PartitioningActor], name = "partitioningActor")

  }

  def callRun(req: Request) {
    val config =
      ConfigFactory.empty
        .withFallback(ConfigFactory.parseString(s"atmos.trace.node = client"))
        .withFallback(ConfigFactory.load())
    val system = ActorSystem("ClusterSystem", config)
    system.actorOf(Props(classOf[CalculationClient], req))
  }

  def worker() {
    val config =
      ConfigFactory.empty
        .withFallback(ConfigFactory.parseString("akka.cluster.roles = [compute]"))
        .withFallback(ConfigFactory.parseString(s"atmos.trace.node = worker ${this.hashCode()}"))
        .withFallback(ConfigFactory.load())

    val system = ActorSystem("ClusterSystem", config)

    system.actorOf(Props[RunningActor], name = "runningActor")
    system.actorOf(Props[PartitioningActor], name = "partitioningActor")
  }
}

class CalculationClient(req: Request) extends Actor with akka.actor.ActorLogging {
  val clusterClient = context.actorOf(Props(classOf[ClusterAwareClient], "/user/partitioningActor"), "client")

  override def preStart(): Unit = {
    import context.dispatcher
    implicit val timeout = Timeout(3660000)
    val results = clusterClient ask req
    results.onComplete {
      case Success(responce: SimulationStatistics) => {
        println(responce.reducedDistribution.mkString("\n"))
        println("calculation id: " + responce.calculationId)
        println("hitting ratio:" + responce.hittingRatio)
        println("simulation loss:" + responce.simulationLoss)
        println("simulation loss reduced:" + responce.simulationLossReduced)
        context.system.shutdown()
        context.system.awaitTermination()
      }
      case Success(x: Any) => log.error("Unexpected message has been received: " + x)
      case Failure(e: Throwable) => {
        context.system.shutdown()
        context.system.awaitTermination()
        log.error("Failed to get an answer", e)
      }
    }
  }

  def receive = {
    case x: Any => log.error("Unexpected message has been received: " + x)
  }
}

