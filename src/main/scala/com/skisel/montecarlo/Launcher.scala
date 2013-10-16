package com.skisel.montecarlo

import language.postfixOps
import com.typesafe.config.ConfigFactory
import akka.actor._
import scala.collection.JavaConverters._
import com.skisel.montecarlo.SimulationProtocol._

object Launcher {
  def main(args: Array[String]): Unit = {
    args.toList match {
      case Nil => seed("2551", "localhost")
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



