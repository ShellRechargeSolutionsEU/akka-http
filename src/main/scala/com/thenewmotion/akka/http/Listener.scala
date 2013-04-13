package com.thenewmotion.akka.http

import javax.servlet.{AsyncEvent, AsyncListener}
import javax.servlet.http.HttpServletResponse
import akka.actor.{ActorSystem, ActorRef}

/**
 * @author Yaroslav Klymko
 */
class Listener(actor: ActorRef, system: ActorSystem) extends AsyncListener {

  import Listener._

  def onComplete(event: AsyncEvent) {
    tell(event, OnComplete)
  }

  def onError(event: AsyncEvent) {
    tell(event, OnError)
  }

  def onStartAsync(event: AsyncEvent) {
    tell(event, OnStartAsync)
  }

  def onTimeout(event: AsyncEvent) {
    tell(event, OnTimeout)
    val asyncContext = event.getAsyncContext

    val res = asyncContext.getResponse.asInstanceOf[HttpServletResponse]
    val (name, value) = HttpExtension(system).ExpiredHeader
    res.addHeader(name, value)
    res.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE)
    asyncContext.complete()
  }

  private def tell(event: AsyncEvent, on: OnEvent) {
    actor ! AsyncEventMessage(event, on)
  }
}

object Listener {
  abstract sealed class OnEvent
  case object OnStartAsync extends OnEvent
  abstract sealed class OnEndAsync extends OnEvent
  case object OnError extends OnEndAsync
  case object OnComplete extends OnEndAsync
  case object OnTimeout extends OnEndAsync

  case class AsyncEventMessage(event: AsyncEvent, on: OnEvent)
}