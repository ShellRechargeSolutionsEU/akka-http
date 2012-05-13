package com.thenewmotion.akka.http

import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import akka.actor.{Props, ActorSystem}
import com.typesafe.config.ConfigFactory

/**
 * @author Yaroslav Klymko
 */
trait AkkaHttp {

  private[http] var _actorSystem: Option[ActorSystem] = None

  private[http] def initAkkaSystem() {
    _actorSystem = Some(newHttpSystem())
    onSystemInit(_actorSystem.get)
  }

  protected def newHttpSystem(): ActorSystem = {
    val name = ConfigFactory.load().getString("akka.http.system-name")
    val system = ActorSystem(name)
    val ext = HttpExtension(system)
    system.actorOf(Props[EndpointsActor], ext.EndpointsName)
    if (ext.LogConfigOnInit) ext.logConfiguration()
    system
  }

  private[http] def destroyAkkaSystem() {
    _actorSystem.foreach {
      system =>
        onSystemDestroy(system)
        system.shutdown()
    }
    _actorSystem = None
  }

  def onSystemInit(system: ActorSystem) {}
  def onSystemDestroy(system: ActorSystem) {}


  private[http] def doActor(req: HttpServletRequest, res: HttpServletResponse) {
    val system = _actorSystem.get
    val props = Props[AsyncActor].withDispatcher("akka.http.actor.dispatcher")
    val actor = system.actorOf(props)

    val asyncContext = req.startAsync()
    asyncContext.setTimeout(HttpExtension(system).AsyncTimeout)
    asyncContext.addListener(new Listener(actor, system))

    actor ! asyncContext
  }
}
