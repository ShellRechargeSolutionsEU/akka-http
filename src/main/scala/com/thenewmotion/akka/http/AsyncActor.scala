package com.thenewmotion.akka.http


import akka.actor._
import Endpoints._
import Async._
import javax.servlet.{ServletRequest, ServletResponse, AsyncContext}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import HttpServletResponse.SC_INTERNAL_SERVER_ERROR
import scala.util.{Failure, Success, Try}


/**
 * @author Yaroslav Klymko
 */
class AsyncActor(val provider: Provider, uri: String) extends Actor with LoggingFSM[State, Data] {
  import Listener._

  implicit def res2HttpRes(res: ServletResponse) = res.asInstanceOf[HttpServletResponse]
  implicit def req2HttpReq(req: ServletRequest) = req.asInstanceOf[HttpServletRequest]

  startWith(AboutToProcess, NoData)

  when(AboutToProcess) {
    case Event(async: AsyncContext, NoData) =>
      debug("about to process async")
      Try(async.getRequest) match {
        case Failure(e) =>
          error(e)
          stop()
        case Success(request) =>
          val endpoint: Endpoint = provider.lift(uri) match {
            case Some(e) => e
            case None =>
              debug("no endpoint found")
              NotFound
          }

          endpoint match {
            case EndpointFunc(func) =>
              debug("processing async")
              Try(func(request)) match {
                case Success(future) => self ! Complete(future)
                case Failure(e) =>
                  error(e)
                  Try {
                    async.getResponse.sendError(SC_INTERNAL_SERVER_ERROR)
                    async.complete()
                  }
              }

              self ! Complete(func(request))
            case EndpointActor(actor) =>
              debug("passing async scope to endpoint actor")
              actor ! request
          }

          goto(AboutToComplete) using AsyncData(async)
      }
  }
  when(AboutToComplete) {
    case Event(Complete(future), AsyncData(async)) =>
      debug("about to complete async")

      def onComplete = future.onComplete.lift.apply _

      Try(async.getResponse) match {
        case Failure(e) =>
          error(e)
          onComplete(Some(e))
        case Success(response) =>
          Try(future(response)) match {
            case Success(_) => onComplete(None)
            case Failure(e) =>
              onComplete(Some(e))
              error(e)
              Try(response.sendError(SC_INTERNAL_SERVER_ERROR))
          }
      }
      Try(async.complete())
      stop()
  }
  whenUnhandled {
    case Event(AsyncEventMessage(_, OnStartAsync), _) => stay()
    case Event(AsyncEventMessage(event, OnTimeout), _) =>
      log.error("[{}] asynchronous processing timed out", uri)
      stop()
    case Event(AsyncEventMessage(_, _: OnEndAsync), _) => stop()
  }
  initialize

  def debug(msg: => String) {
    if (log.isDebugEnabled) log.debug("[{}]: {}", uri, msg)
  }

  def error(e: => Throwable) {
    if (log.isDebugEnabled) log.error(e, "{}: {}", uri, e)
    else log.error("[{}]: {}", uri, e)
  }
}

object Async {
  sealed trait State
  case object AboutToProcess extends State
  case object AboutToComplete extends State

  sealed trait Data
  case class AsyncData(context: AsyncContext) extends Data
  case object NoData extends Data

  case class Complete(func: FutureResponse)
}