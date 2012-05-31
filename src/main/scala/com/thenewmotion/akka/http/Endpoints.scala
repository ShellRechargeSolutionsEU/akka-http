package com.thenewmotion.akka.http

import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import akka.actor.{ActorRef, ActorLogging, Actor}

/**
 * @author Yaroslav Klymko
 */
object Endpoints {

  type Callback = (Boolean => Unit)
  val DummyCallback: Callback = _ => ()

  type Completing = (HttpServletResponse => Callback)
  val DummyCompleting: Completing = _ => DummyCallback

  type Processing = (HttpServletRequest => Completing)
  val DummyProcessing: Processing = (_ => DummyCompleting)

  val NotFound: Processing = (req: HttpServletRequest) => (res: HttpServletResponse) => {
    res.setStatus(HttpServletResponse.SC_NOT_FOUND)
    val writer = res.getWriter
    writer.write("No endpoint available for [" + req.getPathInfo + "]")
    writer.close()
    res.flushBuffer()
    DummyCallback
  }

  type Provider = PartialFunction[String, Endpoint]

  case class Find(url: String)
  case class Found(e: Endpoint)
  case class Attach(name: String, p: Provider)
  case class Detach(name: String)

  sealed abstract class Endpoint
  case class EndpointFunc(func: Processing) extends Endpoint
  case class EndpointActor(a: ActorRef) extends Endpoint

  object Endpoint {
    def apply(a: ActorRef): Endpoint = EndpointActor(a)
    def apply(func: Processing): Endpoint = EndpointFunc(func)
  }

  implicit def actor2Endpoint(a: ActorRef): Endpoint = Endpoint(a)
  implicit def func2Endpoint(func: Processing): Endpoint = Endpoint(func)
}

class EndpointsActor extends Actor with ActorLogging {

  import Endpoints._
  val providers = collection.mutable.Map[String, Provider]()

  protected def receive = {
    case Attach(name, provider) =>
      log.debug("Attaching provider '{}'", name)
      providers += (name -> provider)
    case Detach(name) =>
      log.debug("Detaching provider '{}'", name)
      providers -= name
    case Find(url) =>
      log.debug("Looking for endpoint for '{}'", url)
      val endpoint: Endpoint = providers.toList.collectFirst {
        case (name, provider) if provider.isDefinedAt(url) =>
          log.debug("Endpoint '{}' found for '{}'", name, url)
          provider(url)
      } getOrElse {
        log.debug("No endpoint found for '{}'", url)
        NotFound
      }
      sender ! Found(endpoint)
  }
}