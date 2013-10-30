package com.skisel.montecarlo

import akka.actor.{Actor, ActorRef, ActorPath, Props}
import scala.collection.JavaConverters._
import com.skisel.montecarlo.entity.Loss
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Await
import com.skisel.montecarlo.PartitioningProtocol._
import com.skisel.montecarlo.StorageProtocol._
import com.skisel.montecarlo.SimulationProtocol._
import com.skisel.cluster.{LeaderNodeProtocol, Node}
import com.skisel.cluster.Node
import com.skisel.cluster.LeaderNodeProtocol.JobCompleted

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

  def doWork(workSender: ActorRef, work: Any): Unit = {
    work match {
      case portfolioRequest: SimulatePortfolioRequest => {
        val sim = new MonteCarloSimulator(portfolioRequest.req.inp)
        val events: List[Event] = simulation(portfolioRequest, sim)
        storage ! SaveEvents(events, portfolioRequest.from, portfolioRequest.calculationId)
        for (event <- events) {
          workSender ! AggregationResults(event.eventId, applyStructure(event.losses), portfolioRequest.calculationId)
        }
        self ! JobCompleted
      }
      case loadRequest: LoadPortfolioRequest => {
        implicit val timeout = Timeout(60000)
        val events: List[Event] = Await.result(storage ask loadRequest, timeout.duration).asInstanceOf[List[Event]]
        for (event <- events) {
          workSender ! AggregationResults(event.eventId, applyStructure(event.losses), loadRequest.calculationId)
        }
        self ! JobCompleted
      }
    }
  }

  def simulation(portfolioRequest: SimulatePortfolioRequest, sim: MonteCarloSimulator): List[Event] = {
    ((portfolioRequest.from to portfolioRequest.to) map {
      x => Event(x, simulation(portfolioRequest.req, sim))
    })(collection.breakOut)
  }

  def receive: Actor.Receive = ???
}
