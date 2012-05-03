package com.thenewmotion.akka.http

import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import akka.actor.{ActorLogging, Actor}

/**
 * @author Yaroslav Klymko
 */
object Endpoints {
  /*
  (req: HttpServletRequest) => {
    // ASYNC SCOPE
    // heavy scope, actually this is a place async is needed for
    // async request might expire
    (res: HttpServletResponse) => {
      // RESPONSE SCOPE
      // lite scope for putting collected data to response, called if not expired
      (callback: Boolean) => {
        // CALLBACK SCOPE
        // called to notify whether response sent successfully
      }
    }
  }
  */

  type Callback = (Boolean => Unit)
  val Callback: Callback = (_: Boolean) => ()

  type Completing = (HttpServletResponse => Callback)
  val DummyCompleting: Completing = (_: HttpServletResponse) => Callback

  type Processing = (HttpServletRequest => Completing)

  type Endpoint = (HttpServletRequest => HttpServletResponse => Boolean => Unit)
  val DummyEndpoint: Endpoint = (_ => _ => _ => Unit)
  val NoEndpoint: Endpoint = (req: HttpServletRequest) => (res: HttpServletResponse) => {
    res.setStatus(HttpServletResponse.SC_NOT_FOUND)
    val writer = res.getWriter
    writer.write("No endpoint available for [" + req.getPathInfo + "]")
    writer.flush()
    writer.close()
    Callback
  }

  type Provider = PartialFunction[String, Endpoint]
  case class Find(url: String)
  case class Found(e: Endpoint)
  case class Attach(name: String, p: Provider)
  case class Detach(name: String)
}

class Endpoints extends Actor with ActorLogging {

  import Endpoints._

  private val providers = collection.mutable.Map[String, Provider]()


  protected def receive = {
    case Attach(name, provider) =>
      log.debug("Attaching provider '{}'", name)
      providers += (name -> provider)
    case Detach(name) =>
      log.debug("Detaching provider '{}'", name)
      providers -= name

    case Find(url) =>
      log.debug("Looking for endpoint for '{}'", url)

      val endpoint = providers.toList.collectFirst {
        case (name, provider) if provider.isDefinedAt(url) =>
          log.debug("Endpoint '{}' found for '{}'", name, url)
          provider(url)
      } getOrElse {
        log.debug("Not endpoint found for '{}'", url)
        NoEndpoint
      }

      sender ! Found(endpoint)
  }
}