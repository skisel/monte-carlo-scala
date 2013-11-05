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
      val msg = x.getClass.getSimpleName
      log.info(s"start processing $msg")
      try
        super.receive(x)
      finally {
        val time = System.currentTimeMillis() - start
        log.info(s"stop processing $msg ($time ms)")
      }
  }

}
