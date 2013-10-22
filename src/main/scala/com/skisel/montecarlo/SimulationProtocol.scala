package com.skisel.montecarlo


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

  case class SimulationStatistics(simulationLoss: Double, simulationLossReduced: Double, hittingRatio: Double, reducedDistribution: List[Double], calculationId: String)

}




