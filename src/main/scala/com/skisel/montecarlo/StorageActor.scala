package com.skisel.montecarlo

import akka.actor.Actor
import com.skisel.montecarlo.entity.Loss
import scala.collection.JavaConverters._
import com.skisel.montecarlo.SimulationProtocol._
import com.orientechnologies.orient.core.storage.OStorage
import com.orientechnologies.orient.core.metadata.schema.OClass
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.record.impl.ODocument
import com.skisel.montecarlo.SimulationProtocol.LoadPortfolioRequest
import com.skisel.montecarlo.SimulationProtocol.Event
import com.skisel.montecarlo.SimulationProtocol.InitializeDbCluster
import com.skisel.montecarlo.SimulationProtocol.SaveEvent
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery

class StorageActor extends Actor with akka.actor.ActorLogging {

  val otx: ODatabaseDocumentTx = new ODatabaseDocumentTx("remote:localhost/mc")

  implicit def dbWrapper(db: ODatabaseDocumentTx) = new {
    def queryBySql[T](sql: String, params: AnyRef*): List[T] = {
      val params4java = params.toArray
      val results: java.util.List[T] = db.query(new OSQLSynchQuery[T](sql), params4java: _*)
      results.asScala.toList
    }
  }

  def receive = {
    case InitializeDbCluster(key: Int) => {
      log.info("InitializeDbCluster " + key)
      val db: ODatabaseDocumentTx = otx.open("admin", "admin")
      try {
        val clusterName: String = "a" + key
        var id: Integer = db.getClusterIdByName(clusterName)
        if (id == (-1)) {
          id = db.addCluster(clusterName, OStorage.CLUSTER_TYPE.PHYSICAL, null, null)
        }
        val clazz: OClass = otx.getMetadata.getSchema.getClass("Event")
        if (!clazz.getClusterIds.contains(id)) clazz.addClusterId(id)
        sender ! key
      }
      finally {
        db.close()
      }
    }
    case InitializeCalculation(numOfSimulations: Int) => {
      log.info("InitializeCalculation " + numOfSimulations)
      val db: ODatabaseDocumentTx = otx.open("admin", "admin")
      try {
        val doc: ODocument = db.newInstance()
        doc.field("@class", "Calculation")
        doc.field("numOfSimulations", numOfSimulations)
        db.save(doc)
        val identity: ORID = doc.getIdentity
        sender ! getCalculationId(identity)
      }
      finally {
        db.close()
      }
    }

    case LoadCalculation(calculationId: String) => {
      log.info("LoadCalculation " + calculationId)
      val db: ODatabaseDocumentTx = otx.open("admin", "admin")
      try {
        val list: List[ODocument] = db.queryBySql("select from Calculation where @rid=?", calculationId)
        val field: Integer = list.head.field("numOfSimulations")
        sender ! field.toInt
      }
      finally {
        db.close()
      }
    }

    case SaveEvent(event: Event, key: Int, calculationId: String) => {
      log.debug("SaveEvent " + event)
      val db: ODatabaseDocumentTx = otx.open("admin", "admin")
      try {
        val doc: ODocument = db.newInstance()
        doc.field("@class", "Event")
        doc.field("eventId", event.eventId)
        doc.field("calculationId", calculationId)
        doc.field("losses", Loss.toJson(event.losses.asJava))
        db.save(doc, "a" + key)
      }
      finally {
        db.close()
      }
    }
    case LoadPortfolioRequest(key: Int, _, calculationKey: String, _) => {
      log.info("LoadPortfolioRequest key:" + key + " calculationKey" + calculationKey)
      val db: ODatabaseDocumentTx = otx.open("admin", "admin")
      try {
        val result: List[ODocument] = db.queryBySql("select from cluster:a" + key + " where calculationId=?", calculationKey)
        sender ! (result map {
          x: ODocument => {
            val eventId: Integer = x.field("eventId")
            val losses: java.util.List[Loss] = Loss.fromJson(x.field("losses"))
            Event(eventId.toInt, losses.asScala.toList)
          }
        }).toList
      }
      finally {
        db.close()
      }
    }
    case x: Any => log.error("Unexpected message has been received: " + x)
  }

  def getCalculationId(identity: ORID): String = {
    identity.toString(new java.lang.StringBuilder).toString
  }
}
