package com.skisel.instruments

import akka.actor.Actor

/**
 * User: sergeykisel
 * Date: 02.11.13
 * Time: 20:27
 */
trait ActorStack extends Actor {
  def wrappedReceive: Receive

  def receive: Receive = {
    case x => if (wrappedReceive.isDefinedAt(x)) wrappedReceive(x) else unhandled(x)
  }

}
