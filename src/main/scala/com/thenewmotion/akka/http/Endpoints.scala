package com.thenewmotion.akka.http

import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import akka.actor.{ActorSystem, ActorRef, ActorLogging, Actor}
import ext.Response
import akka.agent.Agent

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

  val NotFound: Processing = (req: HttpServletRequest) => Response(
    HttpServletResponse.SC_NOT_FOUND,
    "No endpoint available for [" + req.getPathInfo + "]")

  type Provider = PartialFunction[String, Endpoint]

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

trait EndpointFinder {
  def find(url: String): Option[Endpoints.Endpoint]
}

class EndpointsAgent(system: ActorSystem)
  extends Agent(Map[String, Endpoints.Provider](), system)
  with EndpointFinder {

  import Endpoints._

  def attach(name: String, p: Provider) {
    send(_ + (name -> p))
  }

  def detach(name: String) {
    send(_ - name)
  }

  def find(url: String) = get().values.collectFirst {
    case provider if provider.isDefinedAt(url) => provider.apply(url)
  }
}