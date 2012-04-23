package com.thenewmotion.akka.http

import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import java.io.{ByteArrayOutputStream, OutputStreamWriter, PrintWriter}
import org.specs2.mock.Mockito
import javax.servlet.{AsyncEvent, AsyncContext}

/**
 * @author Yaroslav Klymko
 */
object AsyncContextMock extends Mockito {

  def apply(): AsyncContext = {

    val req = mock[HttpServletRequest]
    req.getMethod returns "GET"
    req.getPathInfo returns "/test"

    val asyncContext = mock[AsyncContext]
    req.startAsync() returns asyncContext
    asyncContext.setTimeout(10)
    req.getAsyncContext returns asyncContext

    val res = mock[HttpServletResponse]
    res.getWriter returns new PrintWriter(new OutputStreamWriter(new ByteArrayOutputStream()))

    asyncContext.getRequest returns req
    asyncContext.getResponse returns res
    asyncContext
  }
}

//TODO
object AsyncEventMock extends Mockito{

//  def apply(context:AsyncContext): AsyncEvent = {
//
//  }
}
