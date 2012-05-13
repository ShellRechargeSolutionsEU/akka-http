package com.thenewmotion.akka.http

import javax.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServlet}

class AkkaHttpServlet extends HttpServlet with AkkaHttp {

  override def init() {
    super.init()
    initAkkaSystem()
  }

  override def destroy() {
    destroyAkkaSystem()
    super.destroy()
  }

  override def doPost(req: HttpServletRequest, res: HttpServletResponse) {doActor(req, res)}
  override def doPut(req: HttpServletRequest, res: HttpServletResponse) {doActor(req, res)}
  override def doDelete(req: HttpServletRequest, res: HttpServletResponse) {doActor(req, res)}
  override def doTrace(req: HttpServletRequest, res: HttpServletResponse) {doActor(req, res)}
  override def doHead(req: HttpServletRequest, res: HttpServletResponse) {doActor(req, res)}
  override def doGet(req: HttpServletRequest, res: HttpServletResponse) {doActor(req, res)}
}