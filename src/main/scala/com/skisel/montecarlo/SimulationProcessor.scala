package com.skisel.montecarlo

import akka.actor.{ActorRef, Actor, Props}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent._
import scala.collection.JavaConverters._
import scala.util.Failure
import scala.util.Success
import com.skisel.cluster.LeaderConsumer
import com.skisel.montecarlo.Messages._
import com.skisel.montecarlo.entity.Loss
import com.skisel.montecarlo.StorageProtocol.SaveEvents
import com.skisel.montecarlo.StorageProtocol.InitializeCalculation
import com.skisel.montecarlo.StorageProtocol.Event
import com.skisel.montecarlo.StorageProtocol.LoadInput
import com.skisel.montecarlo.StorageProtocol.InitializeDbCluster
import com.skisel.montecarlo.StorageProtocol.LoadCalculation

class SimulationProcessor(node: ActorRef) extends Actor with akka.actor.ActorLogging with LeaderConsumer {
  val settings = Settings(context.system)
  val storage = context.actorOf(Props[StorageActor])

  /*
  import scala.concurrent.duration._
  import akka.actor.SupervisorStrategy._
  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 5, withinTimeRange = 1 minute) {
    case _: TimeoutException => Escalate
    case _: NullPointerException => Restart
    case _: Exception => Escalate
  }
  */

  def partitions(numOfSimulation: Int): Iterator[IndexedSeq[Int]] = {
    (1 to numOfSimulation).grouped(settings.partitionSize)
  }

  def simulation(request: SimulationRequest, sim: MonteCarloSimulator): List[Loss] = {
      request match {
        case SimulateDealPortfolio(_, _) => sim.simulateDeal().asScala.toList
        case SimulateBackgroundPortfolio(_, _) => sim.simulateBackground().asScala.toList
      }
    }

    def applyStructure(losses: List[Loss]): Double = {
      losses.foldRight(0.0)(_.getAmount + _)
    }

    def simulation(portfolioRequest: SimulatePortfolioRequest, sim: MonteCarloSimulator): List[Event] = {
      ((portfolioRequest.from to portfolioRequest.to) map {
        x => Event(x, simulation(portfolioRequest.req, sim))
      })(collection.breakOut)
    }


  def receive = {
    case simulationRequest: SimulationRequest => {
      implicit val timeout = Timeout(30000)
      import context.dispatcher
      val aggregator: ActorRef = context.actorOf(Props(classOf[MonteCarloResultAggregator], node, simulationRequest.numOfSimulations))
      storage.ask(InitializeCalculation(simulationRequest.numOfSimulations)).mapTo[String].onComplete {
        case Success(calculationId) => {
          val eventPartitions: List[IndexedSeq[Int]] = partitions(simulationRequest.numOfSimulations).toList
          val initClustersFutures: List[Future[Int]] =
            for {part <- eventPartitions} yield {
              storage.ask(InitializeDbCluster(part.head)).mapTo[Int]
            }
          Await.result(Future.sequence(initClustersFutures), timeout.duration)
          val jobs: List[SimulatePortfolioRequest] = eventPartitions map (
            partition => SimulatePortfolioRequest(partition.head, partition.last, simulationRequest, calculationId)
          )
          leaderMsg(CalculationPart(jobs),aggregator)
        }
        case Failure(e: Throwable) =>
          node ! SimulationFailed(e)
      }
    }
    case loadRequest: LoadRequest => {
      implicit val timeout = Timeout(30000)
      import context.dispatcher
      storage.ask(LoadCalculation(loadRequest.calculationId)).mapTo[Int].onComplete {
        case Success(numOfSimulations: Int) => {
          val aggregator: ActorRef = context.actorOf(Props(classOf[MonteCarloResultAggregator], node, numOfSimulations))
          val jobs: List[LoadPortfolioRequest] = partitions(numOfSimulations).toList map (
            partition => LoadPortfolioRequest(partition.head, loadRequest, loadRequest.calculationId, numOfSimulations)
          )
          leaderMsg(CalculationPart(jobs),aggregator)
        }
        case Failure(e: Throwable) =>
          node ! SimulationFailed(e)
      }
    }
    case portfolioRequest: SimulatePortfolioRequest => {
      implicit val timeout = Timeout(30000)
      import context.dispatcher
      storage.ask(LoadInput(portfolioRequest.req.inputId)).mapTo[Input].onComplete {
        case Success(inp: Input) => {
          val sim = new MonteCarloSimulator(inp)
          val events: List[Event] = simulation(portfolioRequest, sim)
          storage ! SaveEvents(events, portfolioRequest.from, portfolioRequest.calculationId)
          val results: List[AggregationResults] = events map {
            event => AggregationResults(event.eventId, applyStructure(event.losses))
          }
          node ! CalculationPartResult(results, portfolioRequest.calculationId)
        }
        case Failure(e: Throwable) =>
          node ! SimulationFailed(e)
      }
    }
    case loadRequest: LoadPortfolioRequest => {
      try {
        implicit val timeout = Timeout(60000)
        val events: List[Event] = Await.result(storage ask loadRequest, timeout.duration).asInstanceOf[List[Event]]
        val results: List[AggregationResults] = events map {
          event => AggregationResults(event.eventId, applyStructure(event.losses))
        }
        node ! CalculationPartResult(results, loadRequest.calculationId)
      }
      catch {
        case e:TimeoutException =>
          node ! SimulationFailed(e)
      }
    }
  }

}
