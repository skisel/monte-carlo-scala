package com.skisel.montecarlo

import akka.actor.Actor
import java.io.{FileOutputStream, ObjectOutputStream, ObjectInputStream, FileInputStream}
import scala.collection.JavaConverters._
import java.util.zip.{GZIPInputStream, GZIPOutputStream}
import com.skisel.montecarlo.SimulationProtocol._
import com.orientechnologies.orient.`object`.db.{OObjectDatabaseTx, ODatabaseObjectTx}
import com.skisel.montecarlo.entity.{Event, Loss}
import com.orientechnologies.orient.`object`.iterator.OObjectIteratorClass
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery

class RunningActor extends Actor {

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
      store(outs)
      sender ! AggregationResults(aggregateStructure(outs), portfolioRequest)

    }
    case loadRequest: LoadPortfolioRequest => {
      val eventIdToLosses: List[(Int, List[Loss])] = load(loadRequest.from)
      sender ! AggregationResults(aggregateStructure(eventIdToLosses.toList), loadRequest)
    }

    case _ => println("Something failed router")
  }


  def load(from: Int): List[(Int, List[Loss])] = {
    val key = from
    val db: OObjectDatabaseTx = new OObjectDatabaseTx("remote:localhost/mc").open("admin", "admin")
    try {
      db.getEntityManager.registerEntityClasses("com.skisel.montecarlo.entity")
      val result: java.util.List[Event] = db.query(new OSQLSynchQuery[Event]("select * from Event where key = '" + key + "'"));
      val a = result.asScala.toList
      a map {
        x:Event => {
          val d:Event= db.detachAll(x, true)
          (d.getEventId.toInt, d.getLosses.asScala.toList)
        }
      }
    }
    finally {
      db.close()
    }
  }

  def store(outs: List[(Int, List[Loss])]) {
    val key = outs.head._1
    val db: OObjectDatabaseTx = new OObjectDatabaseTx("remote:localhost/mc").open("admin", "admin")
    try {
      db.getEntityManager.registerEntityClasses("com.skisel.montecarlo.entity")
      for (o <- outs) {
        if (o._2.nonEmpty)
          db.save(new Event(o._1, key, o._2.asJava))
      }
    }
    finally {
      db.close()
    }
  }

  def simulation(portfolioRequest: SimulationProtocol.SimulatePortfolioRequest, sim: MonteCarloSimulator): List[(Int, List[Loss])] = {
    ((portfolioRequest.from to portfolioRequest.to) map {
      x => (x, simulation(portfolioRequest.req, sim))
    }).toList
  }
}
