package com.skisel.montecarlo

/**
 * User: sergeykisel
 * Date: 16.10.13
 * Time: 21:57
 */

import language.postfixOps
import akka.actor._
import akka.cluster.Cluster
import com.skisel.montecarlo.SimulationProtocol._
import com.skisel.cluster.LeaderNodeProtocol.{ItemJobMessage, WorkUnit}
import com.skisel.cluster.LeaderConsumer

object SimulationProtocol {

  abstract class Request() extends WorkUnit{
  }

  abstract class SimulationRequest() extends Request {
    def numOfSimulations: Int

    def inp: String
  }

  case class LoadRequest(calculationId: String) extends Request

  case class SimulateDealPortfolio(numOfSimulations: Int, inp: String) extends SimulationRequest

  case class SimulateBackgroundPortfolio(numOfSimulations: Int, inp: String) extends SimulationRequest

  case class SimulationStatistics(simulationLoss: Double, simulationLossReduced: Double, hittingRatio: Double, reducedDistribution: List[Double], calculationId: String)

  case class SimulationFailed(exception: Throwable)

  case class Calculation(workUnit: WorkUnit) extends ItemJobMessage

}

class CalculationClient(req: Request) extends Actor with akka.actor.ActorLogging with LeaderConsumer {

  override def preStart(): Unit = {
      leaderMsgLater(Calculation(req))
    }

  def receive = {
    case responce: SimulationStatistics => 
      log.info(responce.reducedDistribution.mkString("\n"))
      log.info("calculation id: " + responce.calculationId)
      log.info("hitting ratio:" + responce.hittingRatio)
      log.info("simulation loss:" + responce.simulationLoss)
      log.info("simulation loss reduced:" + responce.simulationLossReduced)
      val cluster: Cluster = Cluster(context.system)
      cluster.leave(cluster.selfAddress)
      context.system.shutdown()
    case failed: SimulationFailed =>
      log.error(failed.exception, "simulation failed")
      val cluster: Cluster = Cluster(context.system)
      cluster.leave(cluster.selfAddress)
      context.system.shutdown()
  }
}
