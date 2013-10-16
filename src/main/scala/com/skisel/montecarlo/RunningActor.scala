package com.skisel.montecarlo

import akka.actor.{Props, Actor}
import scala.collection.JavaConverters._
import com.skisel.montecarlo.SimulationProtocol._
import com.skisel.montecarlo.entity.Loss
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import scala.concurrent.{Await, Future}
import scala.util.Success

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
      val sim = new MonteCarloSimulator(portfolioRequest.req.inp)
      val events: List[Event] = simulation(portfolioRequest, sim)
      implicit val timeout = Timeout(60000)
      Await.result(storage ask InitializeDbCluster(portfolioRequest.from),timeout.duration)
      for (event <- events) {
        storage ! SaveEvent(event, portfolioRequest.from, portfolioRequest.calculationId)
        sender ! AggregationResults(event.eventId, applyStructure(event.losses), portfolioRequest)
      }
    }
    case loadRequest: LoadPortfolioRequest => {
      implicit val timeout = Timeout(60000)
      val events: List[Event] = Await.result(storage ask loadRequest,timeout.duration).asInstanceOf[List[Event]]
      for (event <- events) {
        sender ! AggregationResults(event.eventId, applyStructure(event.losses), loadRequest)
      }
    }
    case x: Any => log.error("Unexpected message has been received: " + x)
  }

  def simulation(portfolioRequest: SimulationProtocol.SimulatePortfolioRequest, sim: MonteCarloSimulator): List[Event] = {
    ((portfolioRequest.from to portfolioRequest.to) map {
      x => Event(x, simulation(portfolioRequest.req, sim))
    }).toList
  }
}
