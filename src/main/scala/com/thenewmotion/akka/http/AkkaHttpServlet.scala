package com.thenewmotion.akka.http

import javax.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServlet}
import akka.actor._


class AkkaHttpServlet extends HttpServlet {

  private[http] var _actorSystem: Option[ActorSystem] = None

  override def init() {
    super.init()

    val system = ActorSystem("http")
    system.actorOf(Props[Endpoints], "endpoints")
    _actorSystem = Some(system)
    onSystemInit(system)
  }

  override def destroy() {
    super.destroy()

    _actorSystem.foreach {
      system =>
        onSystemDestroy(system)
        system.shutdown()
    }
    _actorSystem = None
  }

  def onSystemInit(system: ActorSystem) {}
  def onSystemDestroy(system: ActorSystem) {}

  override def doPost(req: HttpServletRequest, res: HttpServletResponse) {doActor(req, res)}
  override def doPut(req: HttpServletRequest, res: HttpServletResponse) {doActor(req, res)}
  override def doDelete(req: HttpServletRequest, res: HttpServletResponse) {doActor(req, res)}
  override def doTrace(req: HttpServletRequest, res: HttpServletResponse) {doActor(req, res)}
  override def doHead(req: HttpServletRequest, res: HttpServletResponse) {doActor(req, res)}
  override def doGet(req: HttpServletRequest, res: HttpServletResponse) {doActor(req, res)}

  private def doActor(req: HttpServletRequest, res: HttpServletResponse) {
    val system = _actorSystem.get
    val props = Props().withDispatcher("akka.http.actor.dispatcher")

    val asyncContext = req.startAsync()
    asyncContext.setTimeout(system.settings.config.getLong("akka.http.timeout"))

    val actor = system.actorOf(props.withCreator(new AsyncActor))
    asyncContext.addListener(new Listener(actor, system))

    actor ! asyncContext
  }
}