package com.skisel.montecarlo

import com.skisel.montecarlo.entity.Loss

/**
 * User: sergeykisel
 * Date: 22.10.13
 * Time: 11:27
 */
object StorageProtocol {

  case class InitializeDbCluster(key: Int)

  case class SaveEvents(events: List[Event], key: Int, calculationId: String) {
    override def toString: String = s"SaveEvents(List[Event],$key,$calculationId)"
  }

  case class Event(eventId: Int, losses: List[Loss]) {
      override def toString: String = s"Event($eventId,List[Loss])"
    }

  case class InitializeCalculation(numOfSimulations: Int)

  case class LoadCalculation(calculationId: String)

  case class LoadInput(inputId: String)

  case class SaveInput(inp: Input)

}
