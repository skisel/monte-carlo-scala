package com.skisel.montecarlo

import akka.actor.{ActorRef, Actor}
import com.skisel.montecarlo.SimulationProtocol._
import com.skisel.montecarlo.SimulationProtocol.LoadPortfolioRequest
import com.skisel.montecarlo.SimulationProtocol.SimulateDealPortfolio
import com.skisel.montecarlo.SimulationProtocol.SimulateBackgroundPortfolio
import com.skisel.montecarlo.SimulationProtocol.AggregationResults
import com.skisel.montecarlo.SimulationProtocol.SimulationStatistics
import com.skisel.montecarlo.SimulationProtocol.SimulatePortfolioRequest

class MonteCarloResultAggregator(requestor: ActorRef) extends Actor with akka.actor.ActorLogging {

  private[this] var outstandingRequests = Map.empty[Int, Double]

  def receive = {
    case AggregationResults(eventId: Int, amount: Double, request: PortfolioRequest) => {
      val numberOfSimulations: Int = request match {
        case SimulatePortfolioRequest(_, _, SimulateDealPortfolio(numOfSimulations, _), _) => numOfSimulations
        case SimulatePortfolioRequest(_, _, SimulateBackgroundPortfolio(numOfSimulations, _), _) => numOfSimulations
        case LoadPortfolioRequest( _, _, _, numOfSimulations) => numOfSimulations
      }
      outstandingRequests += eventId -> amount
      if (outstandingRequests.size == numberOfSimulations) {
        val distribution: List[Double] = outstandingRequests.toList.map(_._2).sorted
        val simulationLoss: Double = distribution.foldRight(0.0)(_ + _) / numberOfSimulations
        val reducedDistribution: List[Double] = reduceDistribution(distribution, numberOfSimulations)
        val reducedSimulationLoss: Double = reducedDistribution.foldRight(0.0)(_ + _) / 1000
        val hittingRatio: Double = distribution.count(_ > 0).toDouble / numberOfSimulations.toDouble
        val statistics: SimulationStatistics = SimulationStatistics(simulationLoss, reducedSimulationLoss, hittingRatio, reducedDistribution, request.calculationId)
        requestor ! statistics
      }
    }
    case x: Any => log.error("Unexpected message has been received: " + x)
  }


  def reduceDistribution(distribution: List[Double], simulations: Int): List[Double] = {
    val dropTo: Int = simulations / 1000
    val avgFunction = {
      l: List[Double] => l.foldRight(0.0)(_ + _) / l.size
    }
    distribution.grouped(dropTo).map(avgFunction).toList
  }
}
