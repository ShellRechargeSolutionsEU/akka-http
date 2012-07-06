package com.thenewmotion.akka.http

import akka.actor._

/**
 * @author Yaroslav Klymko
 */
class HttpExtension(val system: ActorSystem) extends Extension {
  private val http = system.settings.config.getConfig("akka.http")
  val AsyncTimeout: Long = http.getLong("timeout")
  val LogConfigOnInit = http.getBoolean("log-http-config-on-init")
  val ExpiredHeader: (String, String) =
    http.getString("expired-header-name") -> http.getString("expired-header-value")
  val SupervisorPath = http.getString("supervisor-path")

  def supervisor = system.actorFor("/user/" + SupervisorPath)

  def logConfiguration() {
    system.log.info(http.root().render())
  }
}

object HttpExtension extends ExtensionId[HttpExtension] with ExtensionIdProvider {
  def lookup() = HttpExtension

  def createExtension(system: ExtendedActorSystem) = new HttpExtension(system)
}