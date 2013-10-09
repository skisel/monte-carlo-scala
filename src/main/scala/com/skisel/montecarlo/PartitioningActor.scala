package com.skisel.montecarlo


import language.postfixOps
import akka.actor.Actor
import akka.actor.Props
import akka.routing.FromConfig
import com.skisel.montecarlo.SimulationProtocol._
import com.skisel.montecarlo.SimulationProtocol.AggregationResults
import com.skisel.montecarlo.SimulationProtocol.SimulatePortfolioRequest
import com.skisel.montecarlo.SimulationProtocol.LoadPortfolioRequest
import com.skisel.montecarlo.SimulationProtocol.LoadRequest
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import scala.util.Success


class PartitioningActor extends Actor {

  val actor = context.actorOf(Props[RunningActor].withRouter(FromConfig), name = "runningActorRouter")
  val storage = context.actorOf(Props[StorageActor])

  private[this] var outstandingRequests = Map.empty[Int, Double]

  def partitions(numOfSimulation: Int): Iterator[IndexedSeq[Int]] = {
    (1 to numOfSimulation).grouped(1000)
  }

  def receive = {
    case simulationRequest: SimulationRequest => {
      implicit val timeout = Timeout(5000)
      import context.dispatcher
      val replyTo = sender
      storage.ask(InitializeCalculation(simulationRequest.numOfSimulations)).mapTo[String].onComplete {
        case Success(calculationId) => {
          for (part <- partitions(simulationRequest.numOfSimulations)) {
            actor ! SimulatePortfolioRequest(replyTo, part.head, part.last, simulationRequest, calculationId)
          }
        }
        case o: Any => println("Failed get calc key " + o)
      }
    }
    case loadRequest: LoadRequest => {
      implicit val timeout = Timeout(5000)
      import context.dispatcher
      val replyTo = sender
      storage.ask(LoadCalculation(loadRequest.calculationId)).mapTo[Int].onComplete {
        case Success(numOfSimulations: Int) => {
          for (part <- partitions(numOfSimulations)) {
            actor ! LoadPortfolioRequest(replyTo, part.head, loadRequest, loadRequest.calculationId, numOfSimulations)
          }
        }
        case o: Any => println("Failed get num of sim" + o)
      }

    }

    case AggregationResults(eventId: Int, amount: Double, request: PortfolioRequest) => {
      val numberOfSimulations: Int = request match {
        case SimulatePortfolioRequest(_,_,_,SimulateDealPortfolio(numOfSimulations, _),_) => numOfSimulations
        case SimulatePortfolioRequest(_,_,_,SimulateBackgroundPortfolio(numOfSimulations, _),_) => numOfSimulations
        case LoadPortfolioRequest(_,_,_,_,numOfSimulations) => numOfSimulations
      }
      outstandingRequests += eventId -> amount
      if (outstandingRequests.size == numberOfSimulations) {
        val distribution: List[Double] = outstandingRequests.toList.map(_._2).sorted
        val simulationLoss: Double = distribution.foldRight(0.0)(_ + _) / numberOfSimulations
        val reducedDistribution: List[Double] = reduceDistribution(distribution, numberOfSimulations)
        val reducedSimulationLoss: Double = reducedDistribution.foldRight(0.0)(_ + _) / 1000
        val hittingRatio: Double = distribution.count(_ > 0).toDouble / numberOfSimulations.toDouble
        val statistics: SimulationStatistics = SimulationStatistics(simulationLoss, reducedSimulationLoss, hittingRatio, reducedDistribution, request.calculationId)
        request.requestor ! statistics
      }
    }

    case o: Any => {
      println("Something failed PartitioningActor " + o)
    }
  }


  def reduceDistribution(distribution: List[Double], simulations: Int): List[Double] = {
    val dropTo: Int = simulations / 1000
    val avgFunction = {
      l: List[Double] => l.foldRight(0.0)(_ + _) / l.size
    }
    distribution.grouped(dropTo).map(avgFunction).toList
  }
}
