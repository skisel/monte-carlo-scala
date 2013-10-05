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
    //println("analytical loss: " + inp.getRisks.asScala.toList.map(x => x.getPd * x.getValue).foldRight(0.0)(_ + _))
    ///Launcher.callRun(numOfSimulations, SimulateDealPortfolio(numOfSimulations, inp))
    //Launcher.callRun(numOfSimulations, LoadRequest(numOfSimulations))
    Launcher.seed("2551", "192.168.2.109")
  }
}

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
        callRun(LoadRequest("#"+tail.head))
      }
      case _ => println("error")
    }
  }

  def seed(port: String, host: String) {
    val config =
      ConfigFactory.parseString(s"akka.remote.netty.tcp.port=${port}")
        .withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.hostname=\""+host+"\""))
        .withFallback(ConfigFactory.parseString("akka.cluster.roles = [compute]"))
        .withFallback(ConfigFactory.parseString(s"atmos.trace.node = seed ${port}"))
        .withFallback(ConfigFactory.load())

    val system = ActorSystem("ClusterSystem", config)

    system.actorOf(Props[RunningActor], name = "statsWorker")
    system.actorOf(Props[PartitioningActor], name = "statsService")

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

    system.actorOf(Props[RunningActor], name = "statsWorker")
    system.actorOf(Props[PartitioningActor], name = "statsService")
  }
}

class CalculationClient(req: Request) extends Actor {
  val clusterClient = context.actorOf(Props(classOf[ClusterAwareClient], "/user/statsService"), "client")

  override def preStart(): Unit = {
    import context.dispatcher
    implicit val timeout = Timeout(3660000)
    val results = clusterClient ask req
    results.onSuccess {
      case responce: SimulationStatistics => {
        println(responce.reducedDistribution.mkString("\n"))
        println("calculation id: " + responce.calculationId)
        println("hitting ratio:" + responce.hittingRatio)
        println("simulation loss:" + responce.simulationLoss)
        println("simulation loss reduced:" + responce.simulationLossReduced)
        //println("analytical loss: " + risks.map(x => x.getPd * x.getValue).foldRight(0.0)(_ + _))
        context.system.shutdown()
        context.system.awaitTermination()
      }
      case _ => {
        context.system.shutdown()
        context.system.awaitTermination()
      }
    }
  }

  def receive = {
    case _ => println("errorzz")
  }
}

