package com.skisel.instruments.metrics

import com.skisel.instruments.ActorStack
import akka.actor.ActorLogging
import com.skisel.cluster.LeaderConsumer
import com.skisel.instruments.metrics.Messages.Measurement

/**
 * User: sergeykisel
 * Date: 05.11.13
 * Time: 13:51
 */
object Messages {
  case class Measurement(className: String, time: Long)
}

trait MetricsReceiver extends ActorStack with ActorLogging {
  override def receive = {
    case Measurement(className: String, time: Long) =>
      log.info(s"Processing $className ($time ms)")
    case x: Any => super.receive(x)
  }
}

trait MetricsSender extends ActorStack with ActorLogging with LeaderConsumer {
  override def receive: Receive = {
    case x =>
      val start: Long = System.currentTimeMillis()
      try
        super.receive(x)
      finally {
        val time = System.currentTimeMillis() - start
        val msg = x.getClass.getSimpleName
        leaderMsg(Measurement(msg, time))
      }
  }
}

