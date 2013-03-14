package com.thenewmotion.akka.http


import akka.actor._
import SupervisorStrategy.Stop
import Endpoints._
import Async._
import javax.servlet.{ServletRequest, ServletResponse, AsyncContext}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import HttpServletResponse.SC_INTERNAL_SERVER_ERROR
import org.apache.catalina.connector.ClientAbortException
import java.io.IOException


/**
 * @author Yaroslav Klymko
 */
class AsyncActor(val endpoints: EndpointFinder) extends Actor with LoggingFSM[State, Data] {

  implicit def res2HttpRes(res: ServletResponse) = res.asInstanceOf[HttpServletResponse]
  implicit def req2HttpReq(req: ServletRequest) = req.asInstanceOf[HttpServletRequest]

  startWith(AboutToProcess, Empty)

  when(AboutToProcess) {
    case Event(async: AsyncContext, Empty) =>
      async.addListener(new Listener(self, context.system))

      val url = async.getRequest.getRequestURI
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
    case Event(Complete(future), ctx@Context(async, url)) =>
      log.debug("About to complete async for '{}'", url)

      def tryo(func: => Unit)(onException: Exception => Unit): Option[Throwable] = {
        try {
          func; None
        } catch {
          case e: IOException => Some(e)
          case e: Exception => onException(e); Some(e)
        }
      }

      val res = async.getResponse
      val safeRespond = tryo(future(res)) {
        e =>
          if (log.isDebugEnabled) log.error(e, "{} while responding for '{}': {}", e.getClass.getSimpleName, url, e.getMessage)
          else log.error("{} while responding for '{}': {}", e.getClass.getSimpleName, url, e.getMessage)
          res.sendError(SC_INTERNAL_SERVER_ERROR, e.getMessage)
      }

      val safeComplete = tryo(async.complete()) {
        e =>
          if (log.isDebugEnabled) log.error(e, "{} while completing async for '{}': {} ", e.getClass.getSimpleName, url, e.getMessage)
          log.error("{} while completing async for '{}': {} ", e.getClass.getSimpleName, url, e.getMessage)
      }

      future.onComplete.lift(safeRespond match {
        case None => safeComplete
        case some => some
      })

      stop()
  }

  import Listener._
  whenUnhandled {
    case Event(AsyncEventMessage(_, OnStartAsync), _) => stay()
    case Event(AsyncEventMessage(_, _: OnEndAsync), _) => stop()
  }

  initialize

  def InternalErrorOnException(url: String): PartialFunction[Throwable, Unit] = {
    case e: Exception =>
      log.error(e, "{} while serving request for '{}': {}", e.getClass.getSimpleName, url, e.getMessage)
      self ! Complete(FutureResponse(SC_INTERNAL_SERVER_ERROR, e.getMessage))
  }

  def safeProcess(endpoint: RequestResponse, async: Context) {
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

  case class Complete(func: FutureResponse)
}