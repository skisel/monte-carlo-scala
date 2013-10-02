package com.skisel.montecarlo

import akka.actor.{Props, Actor}
import scala.collection.JavaConverters._
import com.skisel.montecarlo.SimulationProtocol._
import com.skisel.montecarlo.entity.Loss
import akka.pattern.{ask,pipe}
import akka.util.Timeout
import scala.concurrent.Future

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
      val outs: List[(Int, List[Loss])] = simulation(portfolioRequest, sim)
      storage ! portfolioRequest.from
      for(x <- outs) {
        storage ! (x._1, portfolioRequest.from, x._2)
        sender ! AggregationResults(x._1, applyStructure(x._2), portfolioRequest)
      }
    }
    case loadRequest: LoadPortfolioRequest => {
      implicit val timeout = Timeout(60000)
      import context.dispatcher
      val future: Future[(Int, List[Loss])] = (storage ask loadRequest).mapTo[(Int, List[Loss])]
      future map {
        x: (Int, List[Loss]) => AggregationResults(x._1, applyStructure(x._2), loadRequest)
      } pipeTo sender
    }

    case _ => println("Something failed router")
  }

  def simulation(portfolioRequest: SimulationProtocol.SimulatePortfolioRequest, sim: MonteCarloSimulator): List[(Int, List[Loss])] = {
    ((portfolioRequest.from to portfolioRequest.to) map {
      x => (x, simulation(portfolioRequest.req, sim))
    }).toList
  }
}
