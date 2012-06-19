package com.thenewmotion.akka.http

import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import akka.actor.{Props, ActorSystem}
import com.typesafe.config.ConfigFactory
import com.thenewmotion.akka.http.Endpoints._
import scala.Some

/**
 * @author Yaroslav Klymko
 */
trait AkkaHttp {

  private[http] var _httpSystem: Option[(ActorSystem, EndpointsAgent)] = None

  private[http] def initAkkaSystem() {
    val system = newHttpSystem()
    val endpoints = new EndpointsAgent(system)
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
        endpoints.close()
        system.shutdown()
    }
    _httpSystem = None
  }

  def onSystemInit(system: ActorSystem, endpoints: EndpointsAgent) {}
  def onSystemDestroy(system: ActorSystem, endpoints: EndpointsAgent) {}

  private[http] def doActor(req: HttpServletRequest, res: HttpServletResponse) {
    val (system, endpoints) = _httpSystem.get
    val props = Props(new AsyncActor(endpoints)).withDispatcher("akka.http.actor.dispatcher")
    val actor = system.actorOf(props)

    val asyncContext = req.startAsync()
    asyncContext.setTimeout(HttpExtension(system).AsyncTimeout)
    asyncContext.addListener(new Listener(actor, system))

    actor ! asyncContext
  }
}

trait StaticEndpoints {
  self: AkkaHttp =>

  override def onSystemInit(system: ActorSystem, endpoints: EndpointsAgent) {
    endpoints.attach("static", providers)
  }

  def providers: Provider
}