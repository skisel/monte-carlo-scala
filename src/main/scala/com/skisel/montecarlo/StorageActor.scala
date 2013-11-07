package com.skisel.montecarlo

import akka.actor.Actor
import com.skisel.montecarlo.entity.Loss
import scala.collection.JavaConverters._
import com.orientechnologies.orient.core.storage.OStorage
import com.orientechnologies.orient.core.metadata.schema.OClass
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.record.impl.ODocument
import com.skisel.montecarlo.StorageProtocol._
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import com.orientechnologies.orient.core.intent.OIntentMassiveInsert
import com.skisel.montecarlo.Messages.LoadPortfolioRequest
import java.util
import com.skisel.instruments.metrics.{MetricsLevel, MetricsSender}

class StorageActor extends Actor with akka.actor.ActorLogging with MetricsSender {
  def metricsLevel: MetricsLevel = MetricsLevel.DATABASE

  val settings = Settings(context.system)
  val otx: ODatabaseDocumentTx = new ODatabaseDocumentTx(settings.dbUri)

  implicit def dbWrapper(db: ODatabaseDocumentTx) = new {
    def queryBySql[T](sql: String, params: AnyRef*): List[T] = {
      val params4java = params.toArray
      val results: java.util.List[T] = db.query(new OSQLSynchQuery[T](sql), params4java: _*)
      results.asScala.toList
    }
  }

  def doInTransaction[T](f: ODatabaseDocumentTx => T) = {
    val db: ODatabaseDocumentTx = otx.open(settings.dbUsername, settings.dbPassword)
    try {
      f(db)
    } finally {
      db.close()
    }
  }

  def wrappedReceive = {
    case LoadInput(key: String) =>
      doInTransaction((db: ODatabaseDocumentTx) => {
        val list: List[ODocument] = db.queryBySql("select from Input where @rid=?", key)
        sender ! Input.fromJson(list.head.field("binary"))
      })

    case SaveInput(inp: Input) =>
      doInTransaction((db: ODatabaseDocumentTx) => {
        val doc: ODocument = db.newInstance()
        doc.field("@class", "Input")
        doc.field("binary", Input.toJson(inp))
        db.save(doc)
        val identity: ORID = doc.getIdentity
        sender ! getCalculationId(identity)
      })

    case InitializeDbCluster(key: Int) =>
      doInTransaction((db: ODatabaseDocumentTx) => {
        val id: Integer = getClusterId(db, "a" + key)
        val clazz: OClass = getClazz
        if (!clazz.getClusterIds.contains(id)) clazz.addClusterId(id)
        sender ! key
      })

    case InitializeCalculation(numOfSimulations: Int) =>
      doInTransaction((db: ODatabaseDocumentTx) => {
        val doc: ODocument = db.newInstance()
        doc.field("@class", "Calculation")
        doc.field("numOfSimulations", numOfSimulations)
        db.save(doc)
        val identity: ORID = doc.getIdentity
        sender ! getCalculationId(identity)
      })

    case LoadCalculation(calculationId: String) =>
      doInTransaction((db: ODatabaseDocumentTx) => {
        val list: List[ODocument] = db.queryBySql("select from Calculation where @rid=?", calculationId)
        val field: Integer = list.head.field("numOfSimulations")
        sender ! field.toInt
      })

    case SaveEvents(events: List[Event], key: Int, calculationId: String) =>
      doInTransaction((db: ODatabaseDocumentTx) => {
        db.declareIntent(new OIntentMassiveInsert)
        for (event <- events) {
          val doc: ODocument = db.newInstance()
          doc.field("@class", "Event")
          doc.field("eventId", event.eventId)
          doc.field("calculationId", calculationId)
          doc.field("losses", Loss.toJson(event.losses.asJava))
          db.save(doc, "a" + key)
          1
        }
        db.declareIntent(null)
      })

    case LoadPortfolioRequest(key: Int, _, calculationKey: String, _) =>
      doInTransaction((db: ODatabaseDocumentTx) => {
        val queryResult: List[ODocument] = db.queryBySql(s"select from cluster:a$key where calculationId=$calculationKey")
        val events: List[Event] = (queryResult map {
          doc: ODocument => {
            val eventId: Integer = doc.field("eventId")
            val losses: util.List[Loss] = Loss.fromJson(doc.field("losses"))
            doc.reset()
            Event(eventId.toInt, losses.asScala.toList)
          }
        })(collection.breakOut)
        sender ! events
      })

    case x: Any => log.error("Unexpected message has been received: " + x)
  }


  def getClazz: OClass = {
    var clazz: OClass = otx.getMetadata.getSchema.getClass("Event")
    if (clazz == null) clazz = otx.getMetadata.getSchema.createClass("Event")
    clazz
  }

  def getClusterId(db: ODatabaseDocumentTx, clusterName: String): Integer = {
    var id: Integer = db.getClusterIdByName(clusterName)
    if (id == (-1)) {
      id = db.addCluster(clusterName, OStorage.CLUSTER_TYPE.PHYSICAL, null, null)
    }
    id
  }

  def getCalculationId(identity: ORID): String = {
    identity.toString(new java.lang.StringBuilder).toString
  }
}
