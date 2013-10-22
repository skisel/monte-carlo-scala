package com.skisel.montecarlo

import com.skisel.montecarlo.SimulationProtocol._

/**
 * Created with IntelliJ IDEA.
 * User: sergeykisel
 * Date: 22.10.13
 * Time: 11:26
 * To change this template use File | Settings | File Templates.
 */
object PartitioningProtocol {

  abstract class PortfolioRequest() {
    def from: Int

    def req: Request

    def calculationId: String
  }

  case class SimulatePortfolioRequest(from: Int, to: Int, req: SimulationRequest, calculationId: String) extends PortfolioRequest

  case class LoadPortfolioRequest(from: Int, req: LoadRequest, calculationId: String, numOfSimulations: Int) extends PortfolioRequest

  case class AggregationResults(eventId: Int, amount: Double, req: PortfolioRequest)

}
