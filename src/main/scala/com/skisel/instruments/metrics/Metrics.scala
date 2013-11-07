package com.skisel.instruments.metrics

import com.skisel.instruments.ActorStack
import akka.actor.ActorLogging
import com.skisel.cluster.LeaderConsumer
import com.skisel.instruments.metrics.Messages.Measurement
import com.skisel.montecarlo.Settings
import akka.cluster.Cluster

/**
 * User: sergeykisel
 * Date: 05.11.13
 * Time: 13:51
 */

trait Enum[A] {

  trait Value {
    self: A =>
  }

  val values: List[A]
}

sealed trait MetricsLevel extends MetricsLevel.Value

object MetricsLevel extends Enum[MetricsLevel] {

  case object APPLICATION extends MetricsLevel

  case object DATABASE extends MetricsLevel

  val values = List(APPLICATION, DATABASE)
}

object Messages {

  case class Measurement(className: String, duration: Long, actorPath: String, timeStamp: Long, metricsLevel: MetricsLevel, nodeName: String)

}

trait MetricsReceiver extends ActorStack {
  val metricsLogger = akka.event.Logging(context.system, "metrics")
  metricsLogger.info(s"className,metricsLevel,duration,timeStamp")

  override def receive = {
    case Measurement(className: String, duration: Long, path: String, timeStamp: Long, metricsLevel: MetricsLevel, nodeName: String) =>
      metricsLogger.info(s"$nodeName,$className,$metricsLevel,$duration,$timeStamp")
    case x: Any => super.receive(x)
  }
}

trait MetricsSender extends ActorStack with LeaderConsumer {
  val metricsSenderSettings = Settings(context.system)
  private val cluster: Cluster = Cluster(context.system)
  val hostName = cluster.selfAddress.host.get
  val port = cluster.selfAddress.port.get

  def metricsLevel: MetricsLevel

  override def receive: Receive = {
    case x =>
      val start: Long = System.currentTimeMillis()
      try
        super.receive(x)
      finally {
        val time = System.currentTimeMillis() - start
        val msg = x.getClass.getSimpleName
        leaderMsg(Measurement(msg, time, self.path.toStringWithAddress(self.path.address), start, metricsLevel, s"$hostName:$port"))
      }
  }
}

