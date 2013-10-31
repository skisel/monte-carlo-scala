package com.skisel.montecarlo

import com.skisel.montecarlo.SimulationProtocol._
import com.skisel.cluster.LeaderNodeProtocol.WorkUnit

/**
 * Created with IntelliJ IDEA.
 * User: sergeykisel
 * Date: 22.10.13
 * Time: 11:26
 * To change this template use File | Settings | File Templates.
 */
object PartitioningProtocol {

  abstract class PortfolioRequest() extends WorkUnit {
    def from: Int

    def req: Request

    def calculationId: String
  }

  case class SimulatePortfolioRequest(from: Int, to: Int, req: SimulationRequest, calculationId: String) extends PortfolioRequest

  case class LoadPortfolioRequest(from: Int, req: LoadRequest, calculationId: String, numOfSimulations: Int) extends PortfolioRequest

  case class AggregationResults(eventId: Int, amount: Double, calculationId: String)

}
