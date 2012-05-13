package com.thenewmotion.akka.http

import akka.actor._

/**
 * @author Yaroslav Klymko
 */
class HttpExtension(val system: ActorSystem) extends Extension {
  private val http = system.settings.config.getConfig("akka.http")
  val AsyncTimeout: Long = http.getLong("timeout")
  val EndpointsName: String = http.getString("endpoints-actor-name")
  val EndpointRetrievalTimeout: Long = http.getLong("endpoint-retrieval-timeout")
  val LogConfigOnInit = http.getBoolean("log-http-config-on-init")
  val ExpiredHeader: (String, String) =
    http.getString("expired-header-name") -> http.getString("expired-header-value")

  def endpoints: ActorRef = system.actorFor("/user/" + EndpointsName)

  def logConfiguration() {
    system.log.info(http.root().render())
  }
}

object HttpExtension extends ExtensionId[HttpExtension] with ExtensionIdProvider {
  def lookup() = HttpExtension
  def createExtension(system: ExtendedActorSystem) = new HttpExtension(system)
}