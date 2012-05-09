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
    actor ! AsyncEventMessage(event, OnComplete)
  }

  def onError(event: AsyncEvent) {
    actor ! AsyncEventMessage(event, OnError)
  }

  def onStartAsync(event: AsyncEvent) {
    actor ! AsyncEventMessage(event, OnStartAsync)
  }

  def onTimeout(event: AsyncEvent) {
    actor ! AsyncEventMessage(event, OnTimeout)
    val asyncContext = event.getAsyncContext

    val res = asyncContext.getResponse.asInstanceOf[HttpServletResponse]
    val (name, value) = HttpExtension(system).expiredHeader
    res.addHeader(name, value)
    asyncContext.complete()
  }
}

object Listener {
  abstract sealed class OnEvent
  case object OnComplete extends OnEvent
  case object OnError extends OnEvent
  case object OnStartAsync extends OnEvent
  case object OnTimeout extends OnEvent

  case class AsyncEventMessage(event: AsyncEvent, on: OnEvent)
}