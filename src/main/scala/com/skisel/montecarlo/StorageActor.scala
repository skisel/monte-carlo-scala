package com.skisel.montecarlo

import akka.actor.Actor
import com.skisel.montecarlo.entity.{Event, Loss}
import com.orientechnologies.orient.`object`.db.OObjectDatabaseTx
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import com.orientechnologies.orient.`object`.iterator.OObjectIteratorCluster

class StorageActor extends Actor {

  val tx: OObjectDatabaseTx = new OObjectDatabaseTx("remote:localhost/mc")
  tx.getEntityManager.registerEntityClasses("com.skisel.montecarlo.entity")

  implicit def dbWrapper(db: OObjectDatabaseTx) = new {
    def queryBySql[T](sql: String, params: AnyRef*): List[T] = {
      val params4java = params.toArray
      val results: java.util.List[T] = db.query(new OSQLSynchQuery[T](sql), params4java: _*)
      results.asScala.toList
    }
  }

  def receive = {
    case event: (Int, Int, List[Loss]) => {
      val db: OObjectDatabaseTx = tx.open("admin", "admin")
      try {
         db.save(new Event(event._1, event._2, event._3.asJava), event._2.toString)
      }
      finally {
        db.close()
      }
    }
    case from: Int => {
      println("load " + from)
      val key:Integer = from
      val db: OObjectDatabaseTx = tx.open("admin", "admin")
      try {
        val scalaIterable: Iterable[Event] = iterableAsScalaIterable(db.browseCluster[Event](key.toString))
        val result: List[Event] = scalaIterable.toList
        println("loaded " + from)
        result map {
          x: Event => {
            val d: Event = db.detachAll(x, true)
            sender ! (d.getEventId.toInt, d.getLosses.asScala.toList)
          }
        }
      }
      finally {
        db.close()
      }
    }
    case _ => println("StorageActor error")
  }
}
