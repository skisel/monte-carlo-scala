package com.skisel.montecarlo

import com.skisel.cluster.LeaderNodeProtocol._
import com.skisel.montecarlo.StorageProtocol.Event

/**
 * User: sergeykisel
 * Date: 04.11.13
 * Time: 20:36
 */
object Messages {

  abstract class Request() extends WorkUnit {
  }

  abstract class SimulationRequest() extends Request {
    def numOfSimulations: Int

    def inputId: String
  }

  case class LoadRequest(calculationId: String) extends Request

  case class SimulateDealPortfolio(numOfSimulations: Int, inputId: String) extends SimulationRequest

  case class SimulateBackgroundPortfolio(numOfSimulations: Int, inputId: String) extends SimulationRequest

  case class SimulationStatistics(simulationLoss: Double, simulationLossReduced: Double, hittingRatio: Double, reducedDistribution: List[Double], calculationId: String) extends JobCompleted


  abstract class PortfolioRequest() extends WorkUnit {
    def from: Int

    def req: Request

    def calculationId: String
  }

  case class SimulatePortfolioRequest(from: Int, to: Int, req: SimulationRequest, calculationId: String) extends PortfolioRequest

  case class LoadPortfolioRequest(from: Int, req: LoadRequest, calculationId: String, numOfSimulations: Int) extends PortfolioRequest

  case class AggregationResults(eventId: Int, amount: Double)

  case class AggregationRequest(events: List[Event], calculationId: String)

  case class CalculationPartResult(aggregations: List[AggregationResults], calculationId: String) extends JobCompleted

  case class CalculationPart(workUnits: List[WorkUnit]) extends CollectionJobMessage

  case class SimulationFailed(exception: Throwable) extends JobFailed

  case class Calculation(workUnit: WorkUnit) extends ItemJobMessage

}