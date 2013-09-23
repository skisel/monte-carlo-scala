package com.skisel.montecarlo

import akka.actor.ActorRef
import com.skisel.montecarlo.MonteCarloSimulator

object SimulationProtocol {

  abstract class Request() {
    def numOfSimulations: Int
  }
  
  abstract class SimulationRequest() extends Request{
    def sim: MonteCarloSimulator
  }

  case class LoadRequest(numOfSimulations: Int) extends Request

  case class SimulateDealPortfolio(numOfSimulations: Int, sim: MonteCarloSimulator) extends SimulationRequest
  
  case class SimulateBackgroundPortfolio(numOfSimulations: Int, sim: MonteCarloSimulator) extends SimulationRequest
  
  abstract class PortfolioRequest() {
    def requestor: ActorRef
    def from: Int 
    def req: Request
  }
  
  case class SimulatePortfolioRequest(requestor: ActorRef, from: Int, to: Int, req: SimulationRequest) extends PortfolioRequest
  
  case class LoadPortfolioRequest(requestor: ActorRef, from: Int, req: LoadRequest) extends PortfolioRequest

  case class AggregationResults(eventIdToAmount: List[(Int, Double)], req: PortfolioRequest)
  
}
