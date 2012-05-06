package com.thenewmotion.akka.http

import Endpoints._

/**
 * @author Yaroslav Klymko
 */
abstract class StaticAkkaHttpServlet extends AkkaHttpServlet {

  override def onSystemInit(system: ActorHttpSystem) {
    system.endpoints ! Attach("static", providers)
  }

  def providers: Provider
}
