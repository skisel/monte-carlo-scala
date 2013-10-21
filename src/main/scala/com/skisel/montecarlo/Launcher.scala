package com.skisel.montecarlo

import language.postfixOps
import com.typesafe.config.{Config, ConfigFactory}
import akka.actor._
import scala.collection.JavaConverters._
import com.skisel.montecarlo.SimulationProtocol._
import com.orientechnologies.orient.core.config.OGlobalConfiguration

object Launcher {
  def main(args: Array[String]): Unit = {
    args.toList match {
      case Nil => seedRun("2551")
      case "seed" :: Nil => println("please define port number")
      case "seed" :: port :: Nil => seedRun(port)
      case "worker" :: Nil => worker()
      case "sim" :: xs :: Nil => simulationRun(xs.toInt)
      case "load" :: xs :: Nil => loadRun(xs)
      case "client" :: Nil => println("please define operation")
      case "client" :: "sim" :: Nil => println("please define number of simulations")
      case "client" :: "sim" :: tail => {
        val inp = new Input()
        println("analytical loss: " + inp.getRisks.asScala.toList.map(x => x.getPd * x.getValue).foldRight(0.0)(_ + _))
        clientRun(SimulateDealPortfolio(tail.head.toInt, inp))
      }
      case "client" :: "load" :: Nil => println("please define calculation key")
      case "client" :: "load" :: tail => {
        val inp = new Input()
        println("analytical loss: " + inp.getRisks.asScala.toList.map(x => x.getPd * x.getValue).foldRight(0.0)(_ + _))
        clientRun(LoadRequest("#" + tail.head))
      }
      case _ => println("error, unknown command: " + (args mkString " "))
    }
  }

  def seedConfig(implicit port: String = "2551"): Config = {
    ConfigFactory.parseString(s"akka.remote.netty.tcp.port=${port}")
      .withFallback(ConfigFactory.parseString("akka.cluster.roles = [compute]"))
      .withFallback(ConfigFactory.parseString(s"atmos.trace.node = seed ${port}"))
      .withFallback(ConfigFactory.load())
  }

  def workerConfig: Config = {
    ConfigFactory.empty
      .withFallback(ConfigFactory.parseString("akka.cluster.roles = [compute]"))
      .withFallback(ConfigFactory.parseString(s"atmos.trace.node = worker ${this.hashCode()}"))
      .withFallback(ConfigFactory.load())
  }

  def clientConfig: Config = {
    ConfigFactory.empty
      .withFallback(ConfigFactory.parseString(s"atmos.trace.node = client"))
      .withFallback(ConfigFactory.load())
  }


  def seedRun(port: String) {
    val system = ActorSystem("ClusterSystem", seedConfig)
    system.actorOf(Props[RunningActor], name = "runningActor")
    system.actorOf(Props[PartitioningActor], name = "partitioningActor")
  }

  def simulationRun(sims: Int) {
    val system = ActorSystem("ClusterSystem", seedConfig)
    system.actorOf(Props[RunningActor], name = "runningActor")
    system.actorOf(Props[PartitioningActor], name = "partitioningActor")
    system.actorOf(Props(classOf[CalculationClient], SimulateDealPortfolio(sims, new Input())))
  }

  def loadRun(key: String) {
    val system = ActorSystem("ClusterSystem", seedConfig)
    system.actorOf(Props[RunningActor], name = "runningActor")
    system.actorOf(Props[PartitioningActor], name = "partitioningActor")
    system.actorOf(Props(classOf[CalculationClient], LoadRequest("#" + key)))
  }

  def clientRun(req: Request) {
    val system = ActorSystem("ClusterSystem", clientConfig)
    system.actorOf(Props(classOf[CalculationClient], req))
  }

  def worker() {
    val system = ActorSystem("ClusterSystem", workerConfig)
    system.actorOf(Props[RunningActor], name = "runningActor")
    system.actorOf(Props[PartitioningActor], name = "partitioningActor")
  }

}



