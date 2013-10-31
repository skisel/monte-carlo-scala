package com.skisel.montecarlo


import language.postfixOps
import akka.actor.{ActorRef, Actor, Props}
import com.skisel.montecarlo.SimulationProtocol._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.{Future, Await}
import com.skisel.cluster.LeaderNodeProtocol.JobCompleted
import com.skisel.montecarlo.entity.Loss
import com.skisel.montecarlo.StorageProtocol.SaveEvents
import com.skisel.montecarlo.StorageProtocol.InitializeCalculation
import scala.util.Failure
import com.skisel.montecarlo.SimulationProtocol.SimulationFailed
import com.skisel.montecarlo.PartitioningProtocol.AggregationResults
import com.skisel.montecarlo.PartitioningProtocol.SimulatePortfolioRequest
import com.skisel.montecarlo.PartitioningProtocol.LoadPortfolioRequest
import com.skisel.montecarlo.SimulationProtocol.SimulateDealPortfolio
import com.skisel.montecarlo.StorageProtocol.Event
import scala.util.Success
import com.skisel.montecarlo.StorageProtocol.InitializeDbCluster
import com.skisel.montecarlo.StorageProtocol.LoadCalculation
import com.skisel.montecarlo.SimulationProtocol.LoadRequest
import scala.collection.JavaConverters._
import com.skisel.cluster.FacadeProtocol.NotifyLeader


class SimulationProcessor(actorRef: ActorRef) extends Actor with akka.actor.ActorLogging {
  val settings = Settings(context.system)
  val storage = context.actorOf(Props[StorageActor])
  val facade = context.actorSelection("/user/facade")

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
      val aggregator: ActorRef = context.actorOf(Props(classOf[MonteCarloResultAggregator], sender, actorRef, simulationRequest.numOfSimulations))
      storage.ask(InitializeCalculation(simulationRequest.numOfSimulations)).mapTo[String].onComplete {
        case Success(calculationId) => {
          val eventPartitions: List[IndexedSeq[Int]] = partitions(simulationRequest.numOfSimulations).toList
          val initClustersFutures: List[Future[Int]] =
            for {part <- eventPartitions} yield {
              storage.ask(InitializeDbCluster(part.head)).mapTo[Int]
            }
          Await.result(Future.sequence(initClustersFutures), timeout.duration)
          for (part <- eventPartitions) {
            facade.tell(NotifyLeader(SimulatePortfolioRequest(part.head, part.last, simulationRequest, calculationId)), aggregator)
          }
        }
        case Failure(e: Throwable) =>
          sender ! SimulationFailed(e)
          actorRef ! JobCompleted
      }
    }
    case loadRequest: LoadRequest => {
      implicit val timeout = Timeout(30000)
      import context.dispatcher
      val replyTo = sender
      storage.ask(LoadCalculation(loadRequest.calculationId)).mapTo[Int].onComplete {
        case Success(numOfSimulations: Int) => {
          val aggregator: ActorRef = context.actorOf(Props(classOf[MonteCarloResultAggregator], replyTo, actorRef, numOfSimulations))
          for (part <- partitions(numOfSimulations)) {
            facade.tell(NotifyLeader(LoadPortfolioRequest(part.head, loadRequest, loadRequest.calculationId, numOfSimulations)), aggregator)
          }
        }
        case Failure(e: Throwable) =>
          replyTo ! SimulationFailed(e)
          actorRef ! JobCompleted
      }
    }
    case portfolioRequest: SimulatePortfolioRequest => {
      val sim = new MonteCarloSimulator(portfolioRequest.req.inp)
      val events: List[Event] = simulation(portfolioRequest, sim)
      storage ! SaveEvents(events, portfolioRequest.from, portfolioRequest.calculationId)
      for (event <- events) {
        sender ! AggregationResults(event.eventId, applyStructure(event.losses), portfolioRequest.calculationId)
      }
      actorRef ! JobCompleted
    }
    case loadRequest: LoadPortfolioRequest => {
      implicit val timeout = Timeout(60000)
      val events: List[Event] = Await.result(storage ask loadRequest, timeout.duration).asInstanceOf[List[Event]]
      for (event <- events) {
        sender ! AggregationResults(event.eventId, applyStructure(event.losses), loadRequest.calculationId)
      }
      actorRef ! JobCompleted
    }

    case x: Any => log.error("Unexpected message has been received: " + x)
  }

}
