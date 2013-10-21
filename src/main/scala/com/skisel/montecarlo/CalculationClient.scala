package com.skisel.montecarlo

/**
 * Created with IntelliJ IDEA.
 * User: sergeykisel
 * Date: 16.10.13
 * Time: 21:57
 * To change this template use File | Settings | File Templates.
 */
import language.postfixOps
import akka.actor._
import akka.util.Timeout
import com.skisel.montecarlo.SimulationProtocol._
import akka.pattern.ask
import scala.util.{Failure, Success}

class CalculationClient(req: Request) extends Actor with akka.actor.ActorLogging {
  val clusterClient = context.actorOf(Props(classOf[ClusterAwareClient], "/user/partitioningActor"), "client")

  override def preStart(): Unit = {
    implicit val timeout = Timeout(3660000)
    import context.dispatcher
    val results = clusterClient ask req
    results.onComplete {
      case Success(responce: SimulationStatistics) => {
        println(responce.reducedDistribution.mkString("\n"))
        println("calculation id: " + responce.calculationId)
        println("hitting ratio:" + responce.hittingRatio)
        println("simulation loss:" + responce.simulationLoss)
        println("simulation loss reduced:" + responce.simulationLossReduced)
      }
      case Success(x: Any) => log.error("Unexpected message has been received: " + x)
      case Failure(e: Throwable) => {
        log.error("Failed to get an answer", e)
      }
    }
  }

  def receive = {
    case x: Any => log.error("Unexpected message has been received: " + x)
  }
}
