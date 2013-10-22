package com.skisel.montecarlo

import com.skisel.montecarlo.entity.Loss

/**
 * Created with IntelliJ IDEA.
 * User: sergeykisel
 * Date: 22.10.13
 * Time: 11:27
 * To change this template use File | Settings | File Templates.
 */
object StorageProtocol {

  case class InitializeDbCluster(key: Int)

  case class SaveEvents(events: List[Event], key: Int, calculationId: String)

  case class Event(eventId: Int, losses: List[Loss])

  case class InitializeCalculation(numOfSimulations: Int)

  case class LoadCalculation(calculationId: String)

}
