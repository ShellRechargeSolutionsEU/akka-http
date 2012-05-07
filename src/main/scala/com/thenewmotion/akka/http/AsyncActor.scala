package com.thenewmotion.akka.http

import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import akka.actor._
import akka.util.duration._
import javax.servlet.{ServletRequest, ServletResponse, AsyncContext}
import Endpoints._
import Http._
import Async._

/**
 * @author Yaroslav Klymko
 */
class AsyncActor extends Actor with LoggingFSM[State, Data] {

  implicit def res2HttpRes(res: ServletResponse) = res.asInstanceOf[HttpServletResponse]
  implicit def req2HttpReq(req: ServletRequest) = req.asInstanceOf[HttpServletRequest]
  val endpointTimeout = context.system.http.endpointRetrievalTimeout

  startWith(Idle, Empty)

  when(Idle) {
    case Event(async: AsyncContext, Empty) =>
      val url = async.getRequest.getPathInfo
      log.debug("Started async for '{}'", url)
      context.system.http.endpoints ! Find(url)
      goto(Started) using Context(async, url)
  }
  when(Started, endpointTimeout millis) {
    case Event(Found(EndpointFunc(func)), ctx@Context(_, url)) =>
      log.debug("Processing async for '{}'", url)
      safeProcess(func, ctx)
    case Event(Found(EndpointActor(actor)), ctx@Context(async, url)) =>
      log.debug("Passing async processing scope to endpoint actor for '{}'", url)
      safeProcess(actor, ctx)
    case Event(FSM.StateTimeout, ctx@Context(_, url)) =>
      log.debug("No endpoint received within {} millis for '{}'", endpointTimeout, url)
      safeProcess(NotFound, ctx)
  }
  when(Completing) {
    case Event(Complete(completing), ctx@Context(async, url)) =>
      log.debug("Completing async for '{}'", url)

      def doComplete(callback: Callback) {
        val success = try {
          async.complete()
          true
        } catch {
          case e: Exception =>
            log.error(e, "Exception while completing async for '{}'", url)
            false
        }
        callback(success)
      }

      val res = async.getResponse
      try doComplete(completing(res)) catch {
        case e: Exception =>
          log.error("Exception while responding for '{}'", url)
          res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
          doComplete(DummyCallback)
      }
      stop()
  }

  whenUnhandled {
    case Event(Listener.AsyncEventMessage(event, on), Context(_, url)) =>
      import Listener._
      log.debug(on.toString)
      on match {
        case OnStartAsync => stay()
        case OnTimeout | OnComplete => stop()
        case OnError =>
          log.error(event.getThrowable, "Error while processing async for '{}'", url)
          stop()
      }
  }

  initialize

  def InternalErrorOnException(url: String): PartialFunction[Throwable, Unit] = {
    case e: Exception =>
      log.error(e, "Exception while serving request for '{}'", url)
      val internalError = (res: HttpServletResponse) => {
        res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
        DummyCallback
      }
      self ! Complete(internalError)
  }

  def safeProcess(endpoint: Processing, async: Context): State = {
    try self ! Complete(endpoint(async.context.getRequest))
    catch InternalErrorOnException(async.url)

    //we want receive different messages before responding, for example 'Timeout'
    goto(Completing) using async
  }

  def safeProcess(actor: ActorRef, async: Context): State = {
    try actor ! async.context.getRequest
    catch InternalErrorOnException(async.url)

    //actor should respond with Complete(..) message
    goto(Completing) using async
  }
}


object Async {
  sealed trait State
  case object Idle extends State
  case object Started extends State
  case object ProcessingRequest extends State
  case object Completing extends State

  sealed trait Data
  case class Context(context: AsyncContext, url: String) extends Data
  case object Empty extends Data

  case class Complete(func: Completing)
}