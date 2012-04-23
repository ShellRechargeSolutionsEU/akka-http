package com.thenewmotion.akka.http

import javax.servlet.AsyncContext
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import akka.actor.{ActorRef, ActorLogging, Actor}

/**
 * @author Yaroslav Klymko
 */
class AsyncActor(asyncContext: AsyncContext) extends Actor with ActorLogging {
  import AsyncActor._
  import EndpointsActor._
  import context._

  def req: HttpServletRequest = asyncContext.getRequest.asInstanceOf[HttpServletRequest]

  def res: HttpServletResponse = asyncContext.getResponse.asInstanceOf[HttpServletResponse]

  lazy val url = req.getPathInfo

  def endpointsActor: ActorRef = system.actorFor("/user/endpoints")

  protected def receive = {
    case Start =>
      log.debug("Starting async for '{}'", url)
      become(receiveEndpoint orElse receiveEvent)
      endpointsActor ! Find(url)
  }

  private def receiveEndpoint: Receive = {
    case Found(endpoint) =>
      log.debug("Processing async for '{}'", url)

      val complete = try endpoint(req) catch {
        case e: Exception =>
          log.error(e, "Exception while serving request for '{}'", url)
          (res: HttpServletResponse) => {
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
            (b: Boolean) => ()
          }
      }

      become(receiveComplete orElse receiveEvent)

      //we want receive different messages before responding, for example 'Timeout'
      self ! Complete(complete)
  }

  private def receiveComplete: Receive = {
    case Complete(func) =>
      log.debug("Completing async for '{}'", url)

      def safeComplete(completed: Boolean => Unit) {
        try {
          asyncContext.complete()
          completed(true)
        } catch {
          case e: Exception =>
            log.error(e, "Exception while completing async for '{}'", url)
            completed(false)
        }
      }

      try safeComplete(func(res)) catch {
        case e: Exception =>
          log.error("Exception while responding for '{}'", url)
          res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
          safeComplete(_ => ())
      } finally stop(self)
  }

  private def receiveEvent: Receive = {
    case Listener.AsyncEventMessage(event, on) =>
      import Listener._
      log.debug(on.toString)
      on match {
        case OnStartAsync =>
        case OnTimeout | OnComplete => stop(self)
        case OnError =>
          log.error(event.getThrowable, "Error while processing async for '{}'", url)
          stop(self)
      }
  }
}

object AsyncActor {
  case object Start
  case class Complete(func: HttpServletResponse => Boolean => Unit)
}