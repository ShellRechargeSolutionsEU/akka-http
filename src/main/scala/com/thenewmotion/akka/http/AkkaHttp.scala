package com.thenewmotion.akka.http

import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import akka.actor.{Props, ActorSystem}
import com.typesafe.config.ConfigFactory

/**
 * @author Yaroslav Klymko
 */
trait AkkaHttp {

  private[http] var actorSystem: Option[ActorSystem] = None

  private[http] def initAkkaSystem() {
    val system = newHttpSystem()
    actorSystem = Some(system)
    system.actorOf(Props[EndpointsActor], HttpExtension(system).EndpointsName)
    onSystemInit(system)
    system.log.info("Akka Http System '{}' created", system)
    logConfigOnInit()
  }

  protected[http] def logConfigOnInit() {
    actorSystem.foreach {
      system =>
        val ext = HttpExtension(system)
        if (ext.LogConfigOnInit) ext.logConfiguration()
    }
  }

  protected def newHttpSystem(): ActorSystem = {
    val name = ConfigFactory.load().getString("akka.http.system-name")
    ActorSystem(name)
  }

  private[http] def destroyAkkaSystem() {
    actorSystem.foreach {
      system =>
        onSystemDestroy(system)
        system.shutdown()
    }
    actorSystem = None
  }

  def onSystemInit(system: ActorSystem) {}
  def onSystemDestroy(system: ActorSystem) {}


  private[http] def doActor(req: HttpServletRequest, res: HttpServletResponse) {
    val system = actorSystem.get
    val props = Props[AsyncActor].withDispatcher("akka.http.actor.dispatcher")
    val actor = system.actorOf(props)

    val asyncContext = req.startAsync()
    asyncContext.setTimeout(HttpExtension(system).AsyncTimeout)
    asyncContext.addListener(new Listener(actor, system))

    actor ! asyncContext
  }
}
