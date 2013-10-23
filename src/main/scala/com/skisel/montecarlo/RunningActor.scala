package com.skisel.montecarlo

import akka.actor.{Props, Actor}
import scala.collection.JavaConverters._
import com.skisel.montecarlo.entity.Loss
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Await
import com.skisel.montecarlo.PartitioningProtocol._
import com.skisel.montecarlo.StorageProtocol._
import com.skisel.montecarlo.SimulationProtocol._

class RunningActor extends Actor with akka.actor.ActorLogging {

  val storage = context.actorOf(Props[StorageActor])

  def simulation(request: SimulationRequest, sim: MonteCarloSimulator): List[Loss] = {
    request match {
      case SimulateDealPortfolio(_, _) => sim.simulateDeal().asScala.toList
      case SimulateBackgroundPortfolio(_, _) => sim.simulateBackground().asScala.toList
    }
  }

  def applyStructure(losses: List[Loss]): Double = {
    losses.foldRight(0.0)(_.getAmount + _)
  }

  def receive = {
    case portfolioRequest: SimulatePortfolioRequest => {
      log.info("SimulatePortfolioRequest " + portfolioRequest)
      val sim = new MonteCarloSimulator(portfolioRequest.req.inp)
      log.info("Simulate started: " + + portfolioRequest.from)
      val events: List[Event] = simulation(portfolioRequest, sim)
      log.info("Simulate ended: " + portfolioRequest.from)
      storage ! SaveEvents(events, portfolioRequest.from, portfolioRequest.calculationId)
      for (event <- events) {
        sender ! AggregationResults(event.eventId, applyStructure(event.losses), portfolioRequest.calculationId)
      }
      log.info("SimulatePortfolioRequest " + portfolioRequest + " Done")
    }
    case loadRequest: LoadPortfolioRequest => {
      log.info("LoadPortfolioRequest " + loadRequest)
      implicit val timeout = Timeout(60000)
      val events: List[Event] = Await.result(storage ask loadRequest, timeout.duration).asInstanceOf[List[Event]]
      for (event <- events) {
        sender ! AggregationResults(event.eventId, applyStructure(event.losses), loadRequest.calculationId)
      }
      log.info("LoadPortfolioRequest " + loadRequest + " Done")
    }
    case x: Any => log.error("Unexpected message has been received: " + x)
  }

  def simulation(portfolioRequest: SimulatePortfolioRequest, sim: MonteCarloSimulator): List[Event] = {
    ((portfolioRequest.from to portfolioRequest.to) map {
      x => Event(x, simulation(portfolioRequest.req, sim))
    }).toList
  }
}
