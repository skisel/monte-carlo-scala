package com.skisel.montecarlo

import language.postfixOps
import com.typesafe.config.{Config, ConfigFactory}
import akka.actor._
import scala.collection.JavaConverters._
import com.skisel.montecarlo.SimulationProtocol._
import com.orientechnologies.orient.core.config.OGlobalConfiguration
import java.net.InetAddress

object Launcher {
  def main(args: Array[String]): Unit = {
    args.toList match {
      case Nil => seedRun()
      case "seed" :: Nil => seedRun()
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

  def seedConfig(): Config = {
    val port: Int = Option(System.getProperty("port")).getOrElse("2551").toInt
    val hostname: String = Option(System.getProperty("hostname")).getOrElse(InetAddress.getLocalHost.getHostName)
    System.setProperty("akka.cluster.seed-nodes.0",s"akka.tcp://ClusterSystem@$hostname:$port")
    ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port")
      .withFallback(ConfigFactory.parseString("akka.cluster.roles = [compute]"))
      .withFallback(ConfigFactory.parseString(s"atmos.trace.node = seed $port"))
      .withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.hostname = $hostname"))
      .withFallback(ConfigFactory.load())
  }

  def workerConfig: Config = {
    val port: Int = Option(System.getProperty("port")).getOrElse("0").toInt
    val hostname: String = Option(System.getProperty("hostname")).getOrElse(InetAddress.getLocalHost.getHostName)
    ConfigFactory.empty
      .withFallback(ConfigFactory.parseString("akka.cluster.roles = [compute]"))
      .withFallback(ConfigFactory.parseString(s"atmos.trace.node = worker ${this.hashCode()}"))
      .withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.hostname = $hostname"))
      .withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.port = $port"))
      .withFallback(ConfigFactory.load())
  }

  def clientConfig: Config = {
    ConfigFactory.empty
      .withFallback(ConfigFactory.parseString(s"atmos.trace.node = client"))
      .withFallback(ConfigFactory.load())
  }


  def seedRun() {
    val system = ActorSystem("ClusterSystem", seedConfig())
    system.actorOf(Props[RunningActor], name = "runningActor")
    system.actorOf(Props[PartitioningActor], name = "partitioningActor")
  }

  def simulationRun(sims: Int) {
    val system = ActorSystem("ClusterSystem", seedConfig())
    system.actorOf(Props[RunningActor], name = "runningActor")
    system.actorOf(Props[PartitioningActor], name = "partitioningActor")
    system.actorOf(Props(classOf[CalculationClient], SimulateDealPortfolio(sims, new Input())))
  }

  def loadRun(key: String) {
    val system = ActorSystem("ClusterSystem", seedConfig())
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



