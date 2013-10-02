package com.skisel.montecarlo

import akka.actor.Actor
import com.skisel.montecarlo.entity.{Event, Loss}
import com.orientechnologies.orient.`object`.db.{OCommandSQLPojoWrapper, OObjectDatabaseTx}
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import com.orientechnologies.orient.`object`.iterator.OObjectIteratorCluster
import com.skisel.montecarlo.SimulationProtocol.LoadPortfolioRequest
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.storage.OStorage
import com.orientechnologies.orient.core.metadata.schema.OClass

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
    case from: Int => {
      println("create cluster " + from)
      val db: OObjectDatabaseTx = tx.open("admin", "admin")
      try {
        db.reload()
        val clusterName: String = "a" + from
        var id: Integer = db.getClusterIdByName(clusterName)
        if (id==(-1)) {
          id = db.addCluster(clusterName,OStorage.CLUSTER_TYPE.PHYSICAL)
        }
        val clazz: OClass = db.getMetadata.getSchema.getClass(classOf[Event])
        if (!clazz.getClusterIds.contains(id)) clazz.addClusterId(id)


      }
      finally {
        db.close()
      }
    }
    case event: (Int, Int, List[Loss]) => {
      val db: OObjectDatabaseTx = tx.open("admin", "admin")
      try {
        db.save(new Event(event._1, event._2, event._3.asJava), "a" + event._2.toString)
      }
      finally {
        db.close()
      }
    }
    case req: LoadPortfolioRequest => {
      println("load " + req.from)
      val key: Integer = req.from
      val db: OObjectDatabaseTx = tx.open("admin", "admin")
      try {
        val scalaIterable: Iterable[Event] = iterableAsScalaIterable(db.browseCluster[Event]("a" + key.toString))
        val result: List[Event] = scalaIterable.toList
        println("loaded " + req.from)
        result map {
          x: Event => {
            val d: Event = db.detachAll(x, true)
            sender !(d.getEventId.toInt, d.getLosses.asScala.toList)
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
