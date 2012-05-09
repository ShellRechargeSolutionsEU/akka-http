package com.thenewmotion.akka.http

import com.typesafe.config.ConfigFactory
import akka.actor._


/**
 * @author Yaroslav Klymko
 */

object HttpSystem {
  def apply(): ActorSystem = {
    val name = ConfigFactory.load().getString("akka.http.system-name")
    val system = ActorSystem(name)
    system.actorOf(Props[EndpointsActor], HttpExtension(system).endpointsName)
    system
  }
}

class HttpExtension(val system: ActorSystem) extends Extension {
  private val config = system.settings.config
  import config._

  val asyncTimeout: Long = system.settings.config.getLong("akka.http.timeout")
  val endpointsName: String = getString("akka.http.endpoints-path")
  def endpoints: ActorRef = system.actorFor("/user/" + endpointsName)
  val endpointRetrievalTimeout: Long = getLong("akka.http.endpoint-retrieval-timeout")
  val expiredHeader: (String, String) =
    getString("akka.http.expired-header-name") -> getString("akka.http.expired-header-value")
}

object HttpExtension extends ExtensionId[HttpExtension] with ExtensionIdProvider {
  def lookup() = HttpExtension
  def createExtension(system: ExtendedActorSystem) = new HttpExtension(system)
}