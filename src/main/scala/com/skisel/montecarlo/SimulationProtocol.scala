package com.skisel.montecarlo

import akka.actor.ActorRef
import com.skisel.montecarlo.entity.Loss

object SimulationProtocol {

  abstract class Request() {
  }

  abstract class SimulationRequest() extends Request {
    def numOfSimulations: Int
    def inp: Input
  }

  case class LoadRequest(calculationId: String) extends Request

  case class SimulateDealPortfolio(numOfSimulations: Int, inp: Input) extends SimulationRequest

  case class SimulateBackgroundPortfolio(numOfSimulations: Int, inp: Input) extends SimulationRequest

  abstract class PortfolioRequest() {
    def from: Int

    def req: Request

    def calculationId: String
  }

  case class SimulatePortfolioRequest(from: Int, to: Int, req: SimulationRequest, calculationId: String) extends PortfolioRequest

  case class LoadPortfolioRequest(from: Int, req: LoadRequest, calculationId: String, numOfSimulations: Int) extends PortfolioRequest

  case class AggregationResults(eventId: Int, amount: Double, req: PortfolioRequest)

  case class SimulationStatistics(simulationLoss: Double, simulationLossReduced: Double, hittingRatio: Double, reducedDistribution: List[Double], calculationId: String)

  case class InitializeDbCluster(key: Int)

  case class SaveEvents(events: List[Event], key: Int, calculationId: String)

  case class Event(eventId: Int, losses: List[Loss])

  case class InitializeCalculation(numOfSimulations: Int)

  case class LoadCalculation(calculationId: String)

}
