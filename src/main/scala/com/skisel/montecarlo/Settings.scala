package com.skisel.montecarlo

import akka.actor.ActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.ExtendedActorSystem
import com.typesafe.config.Config
import scala.collection.JavaConverters._

class SettingsImpl(config: Config) extends Extension {
  val dbUri: String = config.getString("monte-carlo-scala.db.uri")
  val dbUsername: String = config.getString("monte-carlo-scala.db.username")
  val dbPassword: String = config.getString("monte-carlo-scala.db.password")
  val partitionSize: Int = config.getInt("monte-carlo-scala.partition.size")
  val distributionResolution: Int = config.getInt("monte-carlo-scala.distribution.resolution")
  val seedAddress: List[String] = config.getStringList("akka.cluster.seed-nodes").asScala.toList
}
object Settings extends ExtensionId[SettingsImpl] with ExtensionIdProvider {

  override def lookup = Settings

  override def createExtension(system: ExtendedActorSystem) =
    new SettingsImpl(system.settings.config)

  /**
   * Java API: retrieve the Settings extension for the given system.
   */
  override def get(system: ActorSystem): SettingsImpl = super.get(system)
}
