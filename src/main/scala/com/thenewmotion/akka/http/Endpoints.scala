package com.thenewmotion.akka.http

import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import akka.actor._
import akka.actor.SupervisorStrategy.Stop
import javax.servlet.{ServletRequest, ServletResponse, AsyncContext}
import akka.actor.OneForOneStrategy
import scala.util.{Success, Failure, Try}

/**
 * @author Yaroslav Klymko
 */
object Endpoints {
  type RequestResponse = (HttpServletRequest => FutureResponse)

  case class Attach(name: String, provider: Provider)
  case class Detach(name: String)

  object RequestResponse {
    def apply(func: HttpServletRequest => FutureResponse): RequestResponse = func
    def apply(future: FutureResponse): RequestResponse = apply(req => future)
  }

  val NotFound = RequestResponse(req => FutureResponse(
    HttpServletResponse.SC_NOT_FOUND,
    "No endpoint available for [" + req.getPathInfo + "]"
  ))

  type Provider = PartialFunction[String, Endpoint]

  sealed abstract class Endpoint
  case class EndpointFunc(func: RequestResponse) extends Endpoint
  case class EndpointActor(a: ActorRef) extends Endpoint

  object Endpoint {
    def apply(a: ActorRef): Endpoint = EndpointActor(a)
    def apply(func: RequestResponse): Endpoint = EndpointFunc(func)
  }

  implicit def actor2Endpoint(a: ActorRef): Endpoint = Endpoint(a)
  implicit def func2Endpoint(func: RequestResponse): Endpoint = Endpoint(func)
}

class EndpointsActor extends Actor {

  import Endpoints._

  implicit def res2HttpRes(res: ServletResponse) = res.asInstanceOf[HttpServletResponse]
  implicit def req2HttpReq(req: ServletRequest) = req.asInstanceOf[HttpServletRequest]

  var endpoints = Map[String, Provider]()

  override def supervisorStrategy() = OneForOneStrategy() {
    case _: Exception => Stop
  }

  def receive = {
    case Attach(name, provider) =>
      endpoints = endpoints + (name -> provider)

    case Detach(name) => endpoints = endpoints - name

    case async: AsyncContext =>
      Try(async.getRequest.getRequestURI) match {
        case Failure(e) =>
        case Success(uri) =>
          val provider = endpoints.values.fold[Provider](PartialFunction.empty)(_.orElse(_))
          val props = Props(new AsyncActor(provider, uri)).withDispatcher("akka.http.actor.dispatcher")
          val actor = context.actorOf(props)
          async.addListener(new Listener(actor, context.system))
          actor ! async
      }
  }
}