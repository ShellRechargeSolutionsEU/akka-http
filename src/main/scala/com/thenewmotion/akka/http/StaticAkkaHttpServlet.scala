package com.thenewmotion.akka.http

import Endpoints._
import akka.actor.ActorSystem

/**
 * @author Yaroslav Klymko
 */
@deprecated("Use `AkkaHttpServlet with StaticEndpoints` instead", "0.0.4")
abstract class StaticAkkaHttpServlet extends AkkaHttpServlet {

  override def onSystemInit(system: ActorSystem, endpoints: EndpointsAgent) {
    endpoints.attach("static", providers)
  }

  def providers: Provider
}