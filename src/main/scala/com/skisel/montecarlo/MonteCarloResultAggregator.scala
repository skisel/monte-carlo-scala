package com.skisel.montecarlo

import akka.actor.{ActorRef, Actor}
import com.skisel.montecarlo.Messages._
import com.skisel.instruments.metrics.MetricsSender

class MonteCarloResultAggregator(requestor: ActorRef, numberOfSimulations: Int) extends Actor with akka.actor.ActorLogging with MetricsSender{

  val settings = Settings(context.system)
  private[this] var outstandingRequests = Map.empty[Int, Double]

  def wrappedReceive = {
    case CalculationPartResult(aggregations: List[AggregationResults], calculationId: String) => {
      for (agg <- aggregations) {
        outstandingRequests += agg.eventId -> agg.amount
      }
      if (outstandingRequests.size == numberOfSimulations) {
        val distribution: List[Double] = outstandingRequests.toList.map(_._2).sorted
        val simulationLoss: Double = distribution.foldRight(0.0)(_ + _) / numberOfSimulations
        val reducedDistribution: List[Double] = reduceDistribution(distribution, numberOfSimulations)
        val reducedSimulationLoss: Double = reducedDistribution.foldRight(0.0)(_ + _) / settings.distributionResolution
        val hittingRatio: Double = distribution.count(_ > 0).toDouble / numberOfSimulations.toDouble
        val statistics: SimulationStatistics = SimulationStatistics(simulationLoss, reducedSimulationLoss, hittingRatio, reducedDistribution, calculationId)
        requestor ! statistics
        context.stop(self)
      }
    }
    case e: SimulationFailed =>
      requestor ! e
      context.stop(self)
  }


  def reduceDistribution(distribution: List[Double], simulations: Int): List[Double] = {
    val dropTo: Int = simulations / settings.distributionResolution
    val avgFunction = {
      l: List[Double] => l.foldRight(0.0)(_ + _) / l.size
    }
    distribution.grouped(dropTo).map(avgFunction).toList
  }
}
