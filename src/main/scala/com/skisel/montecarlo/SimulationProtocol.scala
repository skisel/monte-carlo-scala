package com.skisel.montecarlo

import akka.actor.ActorRef

object SimulationProtocol {

  abstract class Request() {
    def numOfSimulations: Int
  }
  
  abstract class SimulationRequest() extends Request{
    def inp: Input
  }

  case class LoadRequest(numOfSimulations: Int) extends Request

  case class SimulateDealPortfolio(numOfSimulations: Int, inp: Input) extends SimulationRequest
  
  case class SimulateBackgroundPortfolio(numOfSimulations: Int, inp: Input) extends SimulationRequest
  
  abstract class PortfolioRequest() {
    def requestor: ActorRef
    def from: Int 
    def req: Request
  }
  
  case class SimulatePortfolioRequest(requestor: ActorRef, from: Int, to: Int, req: SimulationRequest) extends PortfolioRequest
  
  case class LoadPortfolioRequest(requestor: ActorRef, from: Int, req: LoadRequest) extends PortfolioRequest

  case class AggregationResults(eventId:Int, amount: Double, req: PortfolioRequest)

  case class SimulationStatistics(simulationLoss: Double, simulationLossReduced: Double, hittingRatio: Double, reducedDistribution: List[Double])
}
