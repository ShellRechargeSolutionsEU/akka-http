package com.thenewmotion.akka.http

import akka.actor.{ActorRef, ActorSystem}
import com.typesafe.config.ConfigFactory


/**
 * @author Yaroslav Klymko
 */
class ActorHttpSystem(val system: ActorSystem) {

  def endpointsPath: String = system.settings.config.getString("akka.http.endpoints-path")

  def endpoints: ActorRef = system.actorFor("/user/" + endpointsPath)

  def endpointRetrievalTimeout: Long = system.settings.config.getLong("akka.http.endpoint-retrieval-timeout")

  def expiredHeader(): (String, String) = {
    val config = system.settings.config
    config.getString("akka.http.expired-header-name") -> config.getString("akka.http.expired-header-value")
  }

  def asyncTimeout: Long = system.settings.config.getLong("akka.http.timeout")
}

object ActorHttpSystem {

  def apply(): ActorHttpSystem = {
    val name = ConfigFactory.load().getString("akka.http.system-name")
    new ActorHttpSystem(ActorSystem(name))
  }
}
