package com.skisel.montecarlo

import akka.actor.Actor
import java.io.{FileOutputStream, ObjectOutputStream, ObjectInputStream, FileInputStream}
import scala.collection.JavaConverters._
import java.util.zip.{GZIPInputStream, GZIPOutputStream}
import com.skisel.montecarlo.SimulationProtocol._

class RunningActor extends Actor {

  def simulation(request: SimulationRequest, sim: MonteCarloSimulator): List[Loss] = {
    request match {
      case SimulateDealPortfolio(_, _) => sim.simulateDeal().asScala.toList
      case SimulateBackgroundPortfolio(_, _) => sim.simulateBackground().asScala.toList
    }
  }

  def applyStructure(losses: List[Loss]): Double = {
    losses.foldRight(0.0)(_.getAmount+_)
  }

  def aggregateStructure(outs: => List[(Int, List[Loss])]): List[(Int, Double)] = {
    outs map {
      x => (x._1,applyStructure(x._2))
    }
  }

  def receive = {
    case portfolioRequest: SimulatePortfolioRequest => {
      System.out.println("from:" + portfolioRequest.from + " to:" + portfolioRequest.to + " req:" + portfolioRequest.req.getClass.getSimpleName)
      val sim = new MonteCarloSimulator(portfolioRequest.req.inp)
      val outs: List[(Int, List[Loss])] = simulation(portfolioRequest,sim)
      //store(outs)
      sender ! AggregationResults(aggregateStructure(outs), portfolioRequest)

    }
    case loadRequest: LoadPortfolioRequest => {
      val eventIdToLosses: List[(Int, List[Loss])] = load(loadRequest.from)
      sender ! AggregationResults(aggregateStructure(eventIdToLosses.toList), loadRequest)
    }

    case _ => println("Something failed router")
  }


  def load(from: Int): List[(Int, List[Loss])] = {
    val stream: ObjectInputStream = new ObjectInputStream(new GZIPInputStream(new FileInputStream("./data/mc" + from + ".bin.gz")))
    val eventIdToLosses: List[(Int, List[Loss])] = stream.readObject().asInstanceOf[List[(Int, List[Loss])]]
    stream.close()
    eventIdToLosses
  }

  def store(outs: List[(Int, List[Loss])]) {
    val stream: ObjectOutputStream = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream("./data/mc" + outs.head._1 + ".bin.gz", false)))
    stream.writeObject(outs)
    stream.flush()
    stream.close()
  }

  def simulation(portfolioRequest: SimulationProtocol.SimulatePortfolioRequest, sim: MonteCarloSimulator): List[(Int, List[Loss])] = {
    ((portfolioRequest.from to portfolioRequest.to) map {
      x => (x,simulation(portfolioRequest.req, sim))
    }).toList
  }
}
