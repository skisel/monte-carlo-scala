package com.skisel.montecarlo

/**
 * User: sergeykisel
 * Date: 16.10.13
 * Time: 21:57
 */

import language.postfixOps
import akka.actor._
import akka.cluster.Cluster
import com.skisel.cluster.LeaderConsumer
import com.skisel.montecarlo.Messages._
import com.skisel.instruments.metrics.{MetricsLevel, MetricsSender}


class CalculationClient(req: Request) extends Actor with akka.actor.ActorLogging with LeaderConsumer with MetricsSender {

  def metricsLevel: MetricsLevel = MetricsLevel.APPLICATION

  override def preStart(): Unit = {
      leaderMsgLater(Calculation(req))
    }

  def wrappedReceive = {
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
