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

class CalculationClient extends Actor with akka.actor.ActorLogging {

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
