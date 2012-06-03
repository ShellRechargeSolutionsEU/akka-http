package com.thenewmotion.akka.http
package ext

import javax.servlet.http.HttpServletResponse
import Endpoints.{Completing, DummyCallback}

/**
 * @author Yaroslav Klymko
 */
object Response {

  def apply(statusCode: Int, msg: String, headers: (String, String)*): Completing =
    (res: HttpServletResponse) => {
      headers.foreach {
        case (name, value) => res.setHeader(name, value)
      }
      res.setStatus(statusCode)
      val writer = res.getWriter
      writer.write(msg)
      writer.close()
      res.flushBuffer()
      DummyCallback
    }

  def apply(statusCode: Int, headers: (String, String)*): Completing = {
    (res: HttpServletResponse) => {
      headers.foreach {
        case (name, value) => res.setHeader(name, value)
      }
      res.sendError(statusCode)
      DummyCallback
    }
  }
}
