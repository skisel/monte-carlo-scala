package com.skisel.montecarlo

import language.postfixOps
import com.typesafe.config.{Config, ConfigFactory}
import akka.actor._
import java.net.InetAddress
import akka.contrib.pattern.ClusterSingletonManager
import com.skisel.cluster.{Facade, Leader}
import scala.reflect._
import scala.Some
import com.skisel.montecarlo.Messages.SimulateDealPortfolio
import com.skisel.montecarlo.Messages.LoadRequest
import com.skisel.montecarlo.Messages.Request

object Launcher {
  def main(args: Array[String]): Unit = {
    args.toList match {
      case Nil => seedRun()
      case "seed" :: Nil => seedRun()
      case "worker" :: Nil => worker()
      //case "sim" :: xs :: Nil => simulationRun(xs.toInt)
      //case "load" :: xs :: Nil => loadRun(xs)
      case "client" :: Nil => println("please define operation")
      case "client" :: "sim" :: Nil => println("please define number of simulations and input id")
      case "client" :: "sim" :: sims :: Nil => println("please define input id")
      case "client" :: "sim" :: sims :: inputId :: Nil =>
        clientRun(SimulateDealPortfolio(sims.toInt, "#" + inputId))
      case "client" :: "load" :: Nil => println("please define calculation key")
      case "client" :: "load" :: tail => {
        clientRun(LoadRequest("#" + tail.head))
      }
      case _ => println("error, unknown command: " + (args mkString " "))
    }
  }

  def seedConfig(): Config = {
    val port: Int = Option(System.getProperty("port")).getOrElse("2551").toInt
    val hostname: String = Option(System.getProperty("hostname")).getOrElse(InetAddress.getLocalHost.getHostName)
    System.setProperty("akka.cluster.seed-nodes.0", s"akka.tcp://ClusterSystem@$hostname:$port")
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
    val port: Int = Option(System.getProperty("port")).getOrElse("0").toInt
    val hostname: String = Option(System.getProperty("hostname")).getOrElse(InetAddress.getLocalHost.getHostName)
    ConfigFactory.empty
      .withFallback(ConfigFactory.parseString(s"atmos.trace.node = client"))
      .withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.hostname = $hostname"))
      .withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.port = $port"))
      .withFallback(ConfigFactory.load())
  }

  def seedRun() {
    val system = ActorSystem("ClusterSystem", seedConfig())
    system.actorOf(ClusterSingletonManager.props(
      singletonProps = _ ⇒ Props(classOf[Leader[SimulationProcessor]], classTag[SimulationProcessor]), singletonName = "leader",
      terminationMessage = PoisonPill, role = Some("compute")),
      name = "singleton")
    system.actorOf(Props[Facade], name = "facade")
    system.actorOf(Props[StorageActor], name="storageActor")
  }

  def clientRun(req: Request) {
    val system = ActorSystem("ClusterSystem", clientConfig)
    system.actorOf(Props[Facade], name = "facade")
    system.actorOf(Props(classOf[CalculationClient], req))
  }

  def worker() {
    val system: ActorSystem = ActorSystem("ClusterSystem", workerConfig)
    system.actorOf(ClusterSingletonManager.props(
      singletonProps = _ ⇒ Props(classOf[Leader[SimulationProcessor]], classTag[SimulationProcessor]), singletonName = "leader",
      terminationMessage = PoisonPill, role = Some("compute")),
      name = "singleton")
    system.actorOf(Props[StorageActor], name="storageActor")
    system.actorOf(Props[Facade], name = "facade")
  }

}

object SaveInputLauncher {
  class Handler extends Actor {
    def receive = {
      case id: String => println(id)
    }
  }

  def main(args: Array[String]): Unit = {
    val system = ActorSystem("ClusterSystem")
    val storage: ActorRef = system.actorOf(Props[StorageActor])
    val handler: ActorRef = system.actorOf(Props[Handler])
    storage.tell(com.skisel.montecarlo.StorageProtocol.SaveInput(new Input), handler)
  }
}

