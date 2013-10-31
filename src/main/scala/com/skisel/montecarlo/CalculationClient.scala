package com.skisel.montecarlo

/**
 * Created with IntelliJ IDEA.
 * User: sergeykisel
 * Date: 16.10.13
 * Time: 21:57
 * To change this template use File | Settings | File Templates.
 */

import language.postfixOps
import akka.actor._
import com.skisel.montecarlo.SimulationProtocol._
import com.skisel.cluster.FacadeProtocol.NotifyLeaderWhenAvailable
import com.skisel.cluster.LeaderNodeProtocol.WorkUnit

object SimulationProtocol {

  abstract class Request() extends WorkUnit{
  }

  abstract class SimulationRequest() extends Request {
    def numOfSimulations: Int

    def inp: Input
  }

  case class LoadRequest(calculationId: String) extends Request

  case class SimulateDealPortfolio(numOfSimulations: Int, inp: Input) extends SimulationRequest

  case class SimulateBackgroundPortfolio(numOfSimulations: Int, inp: Input) extends SimulationRequest

  case class SimulationStatistics(simulationLoss: Double, simulationLossReduced: Double, hittingRatio: Double, reducedDistribution: List[Double], calculationId: String)

  case class SimulationFailed(exception: Throwable)

}

class CalculationClient(req: Request) extends Actor with akka.actor.ActorLogging {
  val facade = context.actorSelection("/user/facade")

  override def preStart(): Unit = {
      facade ! NotifyLeaderWhenAvailable(req)
    }

  def receive = {
    case responce: SimulationStatistics => 
      log.info(responce.reducedDistribution.mkString("\n"))
      log.info("calculation id: " + responce.calculationId)
      log.info("hitting ratio:" + responce.hittingRatio)
      log.info("simulation loss:" + responce.simulationLoss)
      log.info("simulation loss reduced:" + responce.simulationLossReduced)
    case failed: SimulationFailed =>
      log.error("simulation failed", failed.exception)
  }
}
