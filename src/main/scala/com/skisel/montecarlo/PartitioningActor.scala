package com.skisel.montecarlo


import language.postfixOps
import akka.actor.{ActorRef, Actor, Props}
import com.skisel.montecarlo.PartitioningProtocol._
import com.skisel.montecarlo.SimulationProtocol.{SimulationFailed, LoadRequest, SimulationRequest}
import com.skisel.montecarlo.StorageProtocol.{LoadCalculation, InitializeDbCluster, InitializeCalculation}
import akka.pattern.ask
import akka.util.Timeout
import scala.util.{Failure, Success}
import scala.concurrent.{Future, Await}
import akka.routing.FromConfig

class PartitioningActor extends Actor with akka.actor.ActorLogging {
  val settings = Settings(context.system)
  val storage = context.actorOf(Props[StorageActor])
  context.actorOf(Props(classOf[RunningActor]).withRouter(FromConfig), name = "runningActorRouter")

  def partitions(numOfSimulation: Int): Iterator[IndexedSeq[Int]] = {
    (1 to numOfSimulation).grouped(settings.partitionSize)
  }

  def receive = {
    case simulationRequest: SimulationRequest => {
      implicit val timeout = Timeout(30000)
      import context.dispatcher
      val aggregator: ActorRef = context.actorOf(Props(classOf[MonteCarloResultAggregator], sender, simulationRequest.numOfSimulations))
      storage.ask(InitializeCalculation(simulationRequest.numOfSimulations)).mapTo[String].onComplete {
        case Success(calculationId) => {
          val eventPartitions: List[IndexedSeq[Int]] = partitions(simulationRequest.numOfSimulations).toList
          val initClustersFutures: List[Future[Int]] =
            for {part <- eventPartitions} yield {
              storage.ask(InitializeDbCluster(part.head)).mapTo[Int]
            }
          Await.result(Future.sequence(initClustersFutures),timeout.duration)
          for (part <- eventPartitions) {
            //master.tell(SimulatePortfolioRequest(part.head, part.last, simulationRequest, calculationId), aggregator)
          }
        }
        case Failure(e: Throwable) => sender ! SimulationFailed(e)
      }
    }
    case loadRequest: LoadRequest => {
      implicit val timeout = Timeout(30000)
      import context.dispatcher
      val replyTo = sender
      storage.ask(LoadCalculation(loadRequest.calculationId)).mapTo[Int].onComplete {
        case Success(numOfSimulations: Int) => {
          val aggregator: ActorRef = context.actorOf(Props(classOf[MonteCarloResultAggregator], replyTo, numOfSimulations))
          for (part <- partitions(numOfSimulations)) {
            //master.tell(LoadPortfolioRequest(part.head, loadRequest, loadRequest.calculationId, numOfSimulations), aggregator)
          }
        }
        case Failure(e: Throwable) => replyTo ! SimulationFailed(e)
      }
    }

    case x: Any => log.error("Unexpected message has been received: " + x)
  }

}
