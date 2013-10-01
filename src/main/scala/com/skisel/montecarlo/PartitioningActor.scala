package com.skisel.montecarlo


import language.postfixOps
import akka.actor.Actor
import akka.actor.Props
import akka.routing.FromConfig
import com.skisel.montecarlo.SimulationProtocol._
import com.skisel.montecarlo.SimulationProtocol.AggregationResults
import com.skisel.montecarlo.SimulationProtocol.SimulatePortfolioRequest
import com.skisel.montecarlo.SimulationProtocol.LoadPortfolioRequest
import com.skisel.montecarlo.SimulationProtocol.LoadRequest

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

    case AggregationResults(eventId:Int, amount: Double, request: PortfolioRequest) => {
      outstandingRequests += eventId -> amount
      if (outstandingRequests.size == request.req.numOfSimulations) {
        val distribution: List[Double] = outstandingRequests.toList.map(_._2).sorted
        val simulationLoss: Double = distribution.foldRight(0.0)(_ + _) / request.req.numOfSimulations
        val reducedDistribution: List[Double] = reduceDistribution(distribution, request.req.numOfSimulations)
        val reducedSimulationLoss: Double = reducedDistribution.foldRight(0.0)(_ + _) / 1000
        val hittingRatio: Double = distribution.count(_ > 0).toDouble / request.req.numOfSimulations.toDouble
        request.requestor ! SimulationStatistics(simulationLoss, reducedSimulationLoss, hittingRatio, reducedDistribution)
      }
    }

    case _ => println("Something failed PartitioningActor ")
  }


  def reduceDistribution(distribution: List[Double], simulations: Int): List[Double] = {
    val dropTo: Int = simulations / 1000
    val avgFunction = {
      l: List[Double] => l.foldRight(0.0)(_ + _) / l.size
    }
    distribution.grouped(dropTo).map(avgFunction).toList
  }
}
