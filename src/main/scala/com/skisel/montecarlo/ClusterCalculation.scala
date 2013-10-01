package com.skisel.montecarlo

//#imports

import language.postfixOps
import com.typesafe.config.ConfigFactory
import akka.actor._
import akka.util.Timeout
import scala.collection.JavaConverters._
import com.skisel.montecarlo.SimulationProtocol.{LoadRequest, Request, SimulationStatistics, SimulateDealPortfolio}
import akka.pattern.ask
import com.skisel.montecarlo.entity.Risk


//#imports

//seed 2551
//seed 2552
//worker
//client

object Client {
  def main(args: Array[String]): Unit = {
    val inp = new Input()
    val numOfSimulations: Int = 2000
    println("analytical loss: " + inp.getRisks.asScala.toList.map(x => x.getPd * x.getValue).foldRight(0.0)(_ + _))
    //Launcher.callRun(numOfSimulations, SimulateDealPortfolio(numOfSimulations, inp))
    Launcher.callRun(numOfSimulations, LoadRequest(numOfSimulations))
  }
}

object Launcher {
  def main(args: Array[String]): Unit = {
    val arguments: List[String] = args.head.trim.split(" ").toList
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
    //callRun(numOfSimulations)
  }


  def callRun(numOfSimulations: Int, req: Request) {
    val config =
      ConfigFactory.empty
        .withFallback(ConfigFactory.parseString(s"atmos.trace.node = client"))
        .withFallback(ConfigFactory.load())
    val system = ActorSystem("ClusterSystem", config)
    system.actorOf(Props(classOf[CalculationClient], numOfSimulations, req))
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

class CalculationClient(numOfSimulations: Int, req: Request) extends Actor {
  val clusterClient = context.actorOf(Props(classOf[ClusterAwareClient], "/user/statsService"), "client")

  override def preStart(): Unit = {
    import context.dispatcher
    implicit val timeout = Timeout(3660000)
    val results = clusterClient ask req
    results.onSuccess {
      case responce: SimulationStatistics => {
        println(responce.reducedDistribution.mkString("\n"))
        println("hitting ratio:" + responce.hittingRatio)
        println("simulation loss:" + responce.simulationLoss)
        println("simulation loss reduced:" + responce.simulationLossReduced)
        //println("analytical loss: " + risks.map(x => x.getPd * x.getValue).foldRight(0.0)(_ + _))
        context.system.shutdown()
        context.system.awaitTermination()
      }
    }
  }

  def receive = {
    case _ => println("errorzz")
  }
}

