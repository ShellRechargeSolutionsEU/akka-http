package com.thenewmotion.akka.http

import javax.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServlet}
import akka.actor._


class AkkaHttpServlet extends HttpServlet {

  var actorSystem: Option[ActorSystem] = None

  override def init() {
    actorSystem = Some(HttpSystem())
  }


  override def doPost(req: HttpServletRequest, res: HttpServletResponse) {
    doActor(req, res)
  }


  override def doPut(req: HttpServletRequest, res: HttpServletResponse) {
    doActor(req, res)
  }


  override def doDelete(req: HttpServletRequest, res: HttpServletResponse) {
    doActor(req, res)
  }


  override def doTrace(req: HttpServletRequest, res: HttpServletResponse) {
    doActor(req, res)
  }

  override def doHead(req: HttpServletRequest, res: HttpServletResponse) {
    doActor(req, res)
  }

  override def doGet(req: HttpServletRequest, res: HttpServletResponse) {
    doActor(req, res)
  }

  private def doActor(req: HttpServletRequest, res: HttpServletResponse) {
    val system = actorSystem.get
    val props = Props().withDispatcher("akka.http.actor.dispatcher")

    val asyncContext = req.startAsync()
    asyncContext.setTimeout(system.settings.config.getLong("akka.http.timeout"))

    val actor = system.actorOf(props.withCreator(new AsyncActor(asyncContext)), "async")
    asyncContext.addListener(new Listener(actor, system))

    actor ! AsyncActor.Start
  }


  override def destroy() {
    actorSystem.foreach(_.shutdown())
    actorSystem = None
  }
}

object HttpSystem {

  def apply(): ActorSystem = {
    val system = ActorSystem("http")
    system.actorOf(Props[EndpointsActor], "endpoints")
    system
  }
}