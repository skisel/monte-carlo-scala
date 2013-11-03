package com.skisel.instruments

import akka.actor.ActorLogging

/**
 * User: sergeykisel
 * Date: 02.11.13
 * Time: 20:32
 */

trait MetricsSender extends ActorStack with ActorLogging {
  override def receive: Receive = {
    case x =>
      val start: Long = System.currentTimeMillis()
      log.info(s"start processing $x")
      super.receive(x)
      val time = System.currentTimeMillis() - start
      log.info(s"stop processing $x ($time ms)")
  }

}
