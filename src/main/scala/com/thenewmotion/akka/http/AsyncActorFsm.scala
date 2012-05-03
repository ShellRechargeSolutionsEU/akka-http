package com.thenewmotion.akka.http

import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import akka.actor._
import akka.util.duration._
import javax.servlet.{ServletRequest, ServletResponse, AsyncContext}

/**
 * @author Yaroslav Klymko
 */

/**
 * @author Yaroslav Klymko
 */
class AsyncActorFsm extends Actor with LoggingFSM[Async.State, Async.Data] {

  import Async._
  import Endpoints._
  import context._

  //TODO maybe depends on async timeout?
  val endpointFoundTimeout = system.settings.config.getLong("akka.http.endpoint-retrieval-timeout")
  //  val endpointFoundTimeout = 50

  implicit def res2HttpRes(res: ServletResponse) = res.asInstanceOf[HttpServletResponse]

  implicit def req2HttpReq(req: ServletRequest) = req.asInstanceOf[HttpServletRequest]

  startWith(Idle, Empty)

  when(Idle) {
    case Event(async: AsyncContext, Empty) =>
      val url = async.getRequest.getPathInfo
      log.debug("Started async for '{}'", url)
      actorFor("../endpoints") ! Find(url)
      goto(Started) using Context(async, url)
  }

  when(Started, endpointFoundTimeout millis) {
    case Event(Found(endpoint), ctx@Context(_, url)) =>
      log.debug("Processing async for '{}'", url)
      safeProcess(endpoint, ctx)
    case Event(FSM.StateTimeout, ctx@Context(_, url)) =>
      log.debug("No endpoint received within {} millis for '{}'", endpointFoundTimeout, url)
      safeProcess(NoEndpoint, ctx)
  }
  when(Completing) {
    case Event(Complete(completing), ctx@Context(async, url)) =>
      log.debug("Completing async for '{}'", url)

      def safeComplete(callback: Callback) {
        try {
          async.complete()
          callback(true)
        } catch {
          case e: Exception =>
            log.error(e, "Exception while completing async for '{}'", url)
            callback(false)
        }
      }

      val res = async.getResponse
      try safeComplete(completing(res)) catch {
        case e: Exception =>
          log.error("Exception while responding for '{}'", url)
          res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
          safeComplete(Callback)
      }
      stop()
  }

  whenUnhandled {
    case Event(Listener.AsyncEventMessage(event, on), Context(ctx, url)) =>
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

  def safeProcess(endpoint: Endpoint, asyncContext: Context): State = {
    val complete = try endpoint(asyncContext.context.getRequest) catch {
      case e: Exception =>
        log.error(e, "Exception while serving request for '{}'", asyncContext.url)
        (res: HttpServletResponse) => {
          res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
          Callback
        }
    }

    //we want receive different messages before responding, for example 'Timeout'
    self ! Complete(complete)
    goto(Completing) using asyncContext
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

  case class Complete(func: HttpServletResponse => Boolean => Unit)
}