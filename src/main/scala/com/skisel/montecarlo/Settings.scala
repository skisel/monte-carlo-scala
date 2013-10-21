package com.skisel.montecarlo

import akka.actor.ActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.ExtendedActorSystem
import com.typesafe.config.Config

class SettingsImpl(config: Config) extends Extension {
  val DbUri: String = config.getString("monte-carlo-scala.db.uri")
  val DbUsername: String = config.getString("monte-carlo-scala.db.username")
  val DbPassword: String = config.getString("monte-carlo-scala.db.password")

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
