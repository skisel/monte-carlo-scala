package com.skisel.montecarlo

import akka.actor.Actor
import com.skisel.montecarlo.entity.Loss
import com.orientechnologies.orient.`object`.db.OObjectDatabaseTx
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import com.skisel.montecarlo.SimulationProtocol.LoadPortfolioRequest
import com.orientechnologies.orient.core.storage.OStorage
import com.orientechnologies.orient.core.metadata.schema.OClass
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.record.impl.ODocument

class StorageActor extends Actor {

  val tx: OObjectDatabaseTx = new OObjectDatabaseTx("remote:localhost/mc")
  tx.getEntityManager.registerEntityClasses("com.skisel.montecarlo.entity")
  val otx: ODatabaseDocumentTx = new ODatabaseDocumentTx("remote:localhost/mc")

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
      val db: ODatabaseDocumentTx = tx.open("admin", "admin")
      try {
        db.reload()
        val clusterName: String = "a" + from
        var id: Integer = db.getClusterIdByName(clusterName)
        if (id==(-1)) {
          id = db.addCluster(clusterName,OStorage.CLUSTER_TYPE.PHYSICAL,null,null)
        }
        val clazz: OClass = otx.getMetadata.getSchema.getClass("Event")
        if (!clazz.getClusterIds.contains(id)) clazz.addClusterId(id)


      }
      finally {
        db.close()
      }
    }
    case event: (Int, Int, List[Loss]) => {
      val db: ODatabaseDocumentTx = otx.open("admin", "admin")
      try {
        val doc:ODocument = db.newInstance()
        doc.field("eventId", event._1)
        doc.field("losses", event._3.asJava)
        db.save(doc)
      }
      finally {
        db.close()
      }
    }
    case req: LoadPortfolioRequest => {
      println("load " + req.from)
      val key: Integer = req.from
      val db: ODatabaseDocumentTx = otx.open("admin", "admin")
      try {
        val scalaIterable: Iterable[ODocument] = iterableAsScalaIterable(db.browseCluster("a" + key.toString))
        val result: List[ODocument] = scalaIterable.toList
        println("loaded " + req.from)
        result map {
          x: ODocument => {
            val eventId: Integer = x.field("eventId")
            val losses: java.util.List[Loss] = x.field("losses")
            sender !(eventId.toInt, losses.asScala.toList)
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
