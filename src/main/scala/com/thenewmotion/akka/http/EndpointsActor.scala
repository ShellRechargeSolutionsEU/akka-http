package com.thenewmotion.akka.http

import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import akka.actor.{ActorLogging, Actor}
import akka.actor.Actor._


/**
 * @author Yaroslav Klymko
 */
object EndpointsActor {
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
  type Endpoint = (HttpServletRequest => HttpServletResponse => Boolean => Unit)
  type Provider = PartialFunction[String, Endpoint]
  case class Find(url: String)
  case class Found(e: Endpoint)
  case class Attach(name: String, p: Provider)
  case class Detach(name: String)
}


class EndpointsActor extends Actor with ActorLogging {

  import EndpointsActor._

  private var providers = collection.mutable.Map[String, Provider]()


  def NoEndpointAvailable(url: String): Endpoint = (req: HttpServletRequest) => (res: HttpServletResponse) => {
    res.setStatus(HttpServletResponse.SC_NOT_FOUND)
    val writer = res.getWriter
    writer.write("No endpoint available for [" + url + "]")
    writer.flush()
    writer.close()
    (b: Boolean) => ()
  }

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
        NoEndpointAvailable(url)
      }

      sender ! Found(endpoint)
  }
}