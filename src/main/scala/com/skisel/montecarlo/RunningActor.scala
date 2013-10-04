package com.skisel.montecarlo

import akka.actor.{Props, Actor}
import scala.collection.JavaConverters._
import com.skisel.montecarlo.SimulationProtocol._
import com.skisel.montecarlo.entity.Loss
import akka.pattern.{ask,pipe}
import akka.util.Timeout

class RunningActor extends Actor {

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

  def aggregateStructure(outs: => List[(Int, List[Loss])]): List[(Int, Double)] = {
    outs map {
      x => (x._1, applyStructure(x._2))
    }
  }

  def receive = {
    case portfolioRequest: SimulatePortfolioRequest => {
      System.out.println("from:" + portfolioRequest.from + " to:" + portfolioRequest.to + " req:" + portfolioRequest.req.getClass.getSimpleName)
      val sim = new MonteCarloSimulator(portfolioRequest.req.inp)
      val events: List[Event] = simulation(portfolioRequest, sim)
      storage ! InitializeDbCluster(portfolioRequest.from)
      for(event <- events) {
        storage ! SaveEvent(event,  portfolioRequest.from, portfolioRequest.calculationId)
        sender ! AggregationResults(event.eventId, applyStructure(event.losses), portfolioRequest)
      }
    }
    case loadRequest: LoadPortfolioRequest => {
      implicit val timeout = Timeout(60000)
      import context.dispatcher
      val replyTo = sender
      (storage ask loadRequest).mapTo[Event] map {
        event: (Event) => {
          AggregationResults(event.eventId, applyStructure(event.losses), loadRequest)
        }
      } pipeTo replyTo
    }

    case _ => println("Something failed router")
  }

  def simulation(portfolioRequest: SimulationProtocol.SimulatePortfolioRequest, sim: MonteCarloSimulator): List[Event] = {
    ((portfolioRequest.from to portfolioRequest.to) map {
      x => Event(x, simulation(portfolioRequest.req, sim))
    }).toList
  }
}
