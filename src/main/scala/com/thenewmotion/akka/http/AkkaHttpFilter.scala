package com.thenewmotion.akka.http

import javax.servlet._
import http.{HttpServletResponse, HttpServletRequest}


/**
 * @author Yaroslav Klymko
 */
class AkkaHttpFilter extends Filter with AkkaHttp {

  def init(filterConfig: FilterConfig) {
    initAkkaSystem()
  }

  def destroy() {
    destroyAkkaSystem()
  }


  def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
    def toChain() {
      chain.doFilter(request, response)
    }
    (request, response) match {
      case (req: HttpServletRequest, res: HttpServletResponse) =>
        doActor(req, res)
        toChain()
      case _ => toChain()
    }
  }
}