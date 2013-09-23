package com.skisel.montecarlo

import akka.actor._
import akka.pattern.ask
import com.typesafe.config.ConfigFactory
import akka.util.Timeout
import com.skisel.montecarlo.SimulationProtocol.SimulateDealPortfolio

object ActorApp {
  def main(args: Array[String]) {
    val system = ActorSystem("MonteCarloApp", ConfigFactory.load())
    import system.dispatcher
    val monteCarloSimulator: MonteCarloSimulator = new MonteCarloSimulator()
    val runner: ActorRef = system.actorOf(Props(classOf[PartitioningActor],100))
    implicit val timeout = Timeout(60000)
    val results = runner ask SimulateDealPortfolio(2000,monteCarloSimulator)
    //val results = runner ask LoadRequest(2000)
    results.onSuccess {
      case responce: Map[Int,Double] => {
        println(responce.toList.sortBy(_._2).mkString("\n"))
      }
      system.shutdown()
    }
  }

}

