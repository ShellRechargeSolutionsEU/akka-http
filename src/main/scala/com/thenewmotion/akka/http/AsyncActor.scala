package com.thenewmotion.akka.http

import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import akka.actor._
import akka.util.duration._
import javax.servlet.{ServletRequest, ServletResponse, AsyncContext}
import Endpoints._

/**
 * @author Yaroslav Klymko
 */
class AsyncActor extends Actor with LoggingFSM[Async.State, Async.Data] {

  import Async._
  import context._

  //TODO maybe depends on async timeout?
  val endpointFoundTimeout = system.settings.config.getLong("akka.http.endpoint-retrieval-timeout")

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

  def safeProcess(endpoint: Endpoint, async: Context): State = {
    val complete = try endpoint(async.context.getRequest) catch {
      case e: Exception =>
        log.error(e, "Exception while serving request for '{}'", async.url)
        (res: HttpServletResponse) => {
          res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
          DummyCallback
        }
    }
    //we want receive different messages before responding, for example 'Timeout'
    self ! Complete(complete)
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