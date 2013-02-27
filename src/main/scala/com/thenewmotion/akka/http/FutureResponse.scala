package com.thenewmotion.akka.http

import javax.servlet.http.HttpServletResponse
import java.nio.charset.Charset

/**
 * @author Yaroslav Klymko
 */

trait FutureResponse extends (HttpServletResponse => Unit) {
  def apply(res: HttpServletResponse)
  def onComplete: PartialFunction[Option[Throwable], Unit]
}


object FutureResponse {

  val ok = new FutureResponse {
    def apply(res: HttpServletResponse) {
      res.setStatus(HttpServletResponse.SC_OK)
      res.flushBuffer()
    }

    def onComplete = PartialFunction.empty
  }


  def apply(func: HttpServletResponse => Unit): FutureResponse = new FutureResponse {
    def apply(res: HttpServletResponse) { func(res) }
    def onComplete = { case _ => }
  }

  def apply(statusCode: Int, msg: String, headers: (String, String)*): FutureResponse = apply {
    res =>
      res.setCharacterEncoding(Charset.defaultCharset().toString)
      headers.foreach {
        case (name, value) => res.setHeader(name, value)
      }
      res.setStatus(statusCode)
      val writer = res.getWriter
      writer.write(msg)
      writer.close()
      res.flushBuffer()
  }

  def error(statusCode: Int, headers: (String, String)*): FutureResponse = apply {
    res =>
      res.setCharacterEncoding(Charset.defaultCharset().toString)
      headers.foreach {
        case (name, value) => res.setHeader(name, value)
      }
      res.sendError(statusCode)
  }
}
