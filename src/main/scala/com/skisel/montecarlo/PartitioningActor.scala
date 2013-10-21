package com.skisel.montecarlo


import language.postfixOps
import akka.actor.{ActorRef, Actor, Props}
import akka.routing.FromConfig
import com.skisel.montecarlo.SimulationProtocol._
import akka.pattern.ask
import akka.util.Timeout
import scala.util.Success
import scala.concurrent.{Future, Await}


class PartitioningActor extends Actor with akka.actor.ActorLogging {

  val actor = context.actorOf(Props[RunningActor].withRouter(FromConfig), name = "runningActorRouter")
  val storage = context.actorOf(Props[StorageActor])

  def partitions(numOfSimulation: Int): List[IndexedSeq[Int]] = {
    (1 to numOfSimulation).grouped(1000).toList
  }

  def receive = {
    case simulationRequest: SimulationRequest => {
      implicit val timeout = Timeout(30000)
      import context.dispatcher
      val aggregator: ActorRef = context.actorOf(Props(classOf[MonteCarloResultAggregator], sender, simulationRequest.numOfSimulations))
      storage.ask(InitializeCalculation(simulationRequest.numOfSimulations)).mapTo[String].onComplete {
        case Success(calculationId) => {
          val eventPartitions: List[IndexedSeq[Int]] = partitions(simulationRequest.numOfSimulations)
          val initClustersFutures: List[Future[Int]] =
            for {part <- eventPartitions} yield {
              storage.ask(InitializeDbCluster(part.head)).mapTo[Int]
            }
          Await.result(Future.sequence(initClustersFutures),timeout.duration)
          for (part <- eventPartitions) {
            actor.tell(SimulatePortfolioRequest(part.head, part.last, simulationRequest, calculationId), aggregator)
          }
        }
        case x: Any => log.error("Unexpected message has been received: " + x)
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
            actor.tell(LoadPortfolioRequest(part.head, loadRequest, loadRequest.calculationId, numOfSimulations), aggregator)
          }
        }
        case x: Any => log.error("Unexpected message has been received: " + x)
      }
    }

    case x: Any => log.error("Unexpected message has been received: " + x)
  }

}
