package com.skisel.montecarlo

import akka.actor.{Props, Actor}
import akka.routing.RoundRobinRouter
import com.skisel.montecarlo.SimulationProtocol._

class PartitioningActor(partitionSize: Int) extends Actor {
  
  val actor = context.actorOf(Props[RunningActor].withRouter(RoundRobinRouter(nrOfInstances = 1)))
  private[this] var outstandingRequests = Map.empty[Int, Double]

  def partitions(numOfSimulation: Int): Iterator[IndexedSeq[Int]] = {
    (1 to numOfSimulation).grouped(partitionSize)
  }

  def receive = {
    case simulationRequest: SimulationRequest => {
      for (part <- partitions(simulationRequest.numOfSimulations)) {
        actor ! SimulatePortfolioRequest(sender, part.head, part.last, simulationRequest)
      }
    }
    case loadRequest: LoadRequest => {
      for (part <- partitions(loadRequest.numOfSimulations)) {
        actor ! LoadPortfolioRequest(sender, part.head, loadRequest)
      }
    }

    case AggregationResults(eventIdToAmount, request: PortfolioRequest) => {
      for (tuple: (Int, Double) <- eventIdToAmount) {
        outstandingRequests += tuple._1 -> tuple._2
      }
      if (outstandingRequests.size == request.req.numOfSimulations) {
        request.requestor ! outstandingRequests
      }
    }

    case _ => println("Something failed PartitioningActor " )
  }

}
