package com.thenewmotion.akka.http


import akka.actor._
import akka.util.duration._
import Endpoints._
import Async._
import ext.Response
import javax.servlet.{ServletRequest, ServletResponse, AsyncContext}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import HttpServletResponse.SC_INTERNAL_SERVER_ERROR


/**
 * @author Yaroslav Klymko
 */
class AsyncActor(val endpoints: EndpointFinder) extends Actor with LoggingFSM[State, Data] {

  implicit def res2HttpRes(res: ServletResponse) = res.asInstanceOf[HttpServletResponse]
  implicit def req2HttpReq(req: ServletRequest) = req.asInstanceOf[HttpServletRequest]

  startWith(AboutToProcess, Empty)

  when(AboutToProcess) {
    case Event(async: AsyncContext, Empty) =>
      val url = async.getRequest.getPathInfo
      log.debug("About to process async for '{}'", url)

      val endpoint: Endpoint = endpoints.find(url) match {
        case Some(e) => e
        case None =>
          log.debug("No endpoint found for '{}'", url)
          NotFound
      }

      val ctx = Context(async, url)

      endpoint match {
        case EndpointFunc(func) =>
          log.debug("Processing async for '{}'", url)
          safeProcess(func, ctx)
        case EndpointActor(actor) =>
          log.debug("Passing async processing scope to endpoint actor for '{}'", url)
          safeProcess(actor, ctx)
      }

      goto(AboutToComplete) using ctx
  }

  when(AboutToComplete) {
    case Event(Complete(completing), ctx@Context(async, url)) =>
      log.debug("About to complete async for '{}'", url)

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
          log.error(e, "Exception while responding for '{}'", url)
          res.sendError(SC_INTERNAL_SERVER_ERROR, e.getMessage)
          doComplete(DummyCallback)
      }
      stop()
  }

  whenUnhandled {
    case Event(Listener.AsyncEventMessage(event, on), Context(_, url)) =>
      import Listener._
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
      self ! Complete(Response(SC_INTERNAL_SERVER_ERROR, e.getMessage))
  }

  def safeProcess(endpoint: Processing, async: Context) {
    try self ! Complete(endpoint(async.context.getRequest))
    catch InternalErrorOnException(async.url)
  }

  def safeProcess(actor: ActorRef, async: Context) {
    try actor ! async.context.getRequest
    catch InternalErrorOnException(async.url)
  }
}


object Async {
  sealed trait State
  case object AboutToProcess extends State
  case object AboutToComplete extends State

  sealed trait Data
  case class Context(context: AsyncContext, url: String) extends Data
  case object Empty extends Data

  case class Complete(func: Completing)
}