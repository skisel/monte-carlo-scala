package com.skisel.montecarlo

import com.skisel.montecarlo.SimulationProtocol._
import com.skisel.cluster.LeaderNodeProtocol.{ItemJobMessage, CollectionJobMessage, WorkUnit}

/**
 * User: sergeykisel
 * Date: 22.10.13
 * Time: 11:26
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

  case class CalculationPart(workUnits: List[WorkUnit]) extends CollectionJobMessage

}
