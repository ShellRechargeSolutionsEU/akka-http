package com.thenewmotion.akka.http

import akka.actor.ActorSystem

/**
 * @author Yaroslav Klymko
 */
object Http extends Http

trait Http {

  implicit def systemWithHttp(system: ActorSystem) = new {
    def http: ActorHttpSystem = new ActorHttpSystem(system)
  }

  implicit def http2System(http: ActorHttpSystem): ActorSystem = http.system
}
