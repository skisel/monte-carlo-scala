package com.skisel.montecarlo

import org.scalatest._
import akka.actor.{Props, Actor, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import com.skisel.montecarlo.entity.{Risk, Loss}

class StorageActorSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
with WordSpecLike with MustMatchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("StorageActorSpec"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "StorageActor" must {

    "storage works" in {
      val storage = system.actorOf(Props[StorageActor])
      val loss: Loss = new Loss
      val risk: Risk = new Risk()
      risk.setPd(0.05)
      risk.setValue(100)
      loss.setAmount(100)
      loss.setRisk(risk)
      val losses:List[Loss] = List(loss)
      val list: List[(Int, List[Loss])] = List((1, losses), (2, losses))
      storage ! (1, 1, losses)
      storage ! (2, 1, losses)
      Thread.sleep(1000)
      storage ! 1
      expectMsg(list)
    }

  }
}