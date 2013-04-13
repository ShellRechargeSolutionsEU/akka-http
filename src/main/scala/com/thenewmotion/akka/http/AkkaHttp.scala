package com.thenewmotion.akka.http

import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import akka.actor.{ActorRef, Props, ActorSystem}
import com.typesafe.config.ConfigFactory
import com.thenewmotion.akka.http.Endpoints._
import scala.Some

/**
 * @author Yaroslav Klymko
 */
trait AkkaHttp {

  private[http] var _httpSystem: Option[(ActorSystem, ActorRef)] = None

  private[http] def initAkkaSystem() {
    val system = newHttpSystem()
    val endpoints = system.actorOf(Props[EndpointsActor], HttpExtension(system).SupervisorPath)

    _httpSystem = Some(system -> endpoints)
    onSystemInit(system, endpoints)
    system.log.info("Akka Http System '{}' created", system)
    logConfigOnInit()
  }

  protected[http] def logConfigOnInit() {
    _httpSystem.foreach {
      case (system, _) =>
        val ext = HttpExtension(system)
        if (ext.LogConfigOnInit) ext.logConfiguration()
    }
  }

  protected def newHttpSystem(): ActorSystem = {
    val name = ConfigFactory.load().getString("akka.http.system-name")
    ActorSystem(name)
  }

  private[http] def destroyAkkaSystem() {
    _httpSystem.foreach {
      case (system, endpoints) =>
        onSystemDestroy(system, endpoints)
        system.shutdown()
        system.awaitTermination()
    }
    _httpSystem = None
  }

  def onSystemInit(system: ActorSystem, endpoints: ActorRef) {}
  def onSystemDestroy(system: ActorSystem, endpoints: ActorRef) {}

  private[http] def doActor(req: HttpServletRequest, res: HttpServletResponse) {
    _httpSystem.foreach {
      case (system, _) =>
        val asyncContext = req.startAsync()
        val extension = HttpExtension(system)
        asyncContext.setTimeout(extension.AsyncTimeout)
        extension.supervisor ! asyncContext
    }
  }
}

trait StaticEndpoints {
  self: AkkaHttp =>

  override def onSystemInit(system: ActorSystem, endpoints: ActorRef) {
    endpoints ! Attach("static", providers)
  }

  def providers: Provider
}