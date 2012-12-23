package com.thenewmotion.akka.http

import akka.actor.{Props, OneForOneStrategy, Actor}
import akka.actor.SupervisorStrategy.Stop
import javax.servlet.AsyncContext

/**
 * @author Yaroslav Klymko
 */
class AsyncSupervisor(val endpoints: EndpointFinder) extends Actor {

  override def supervisorStrategy() = OneForOneStrategy() {
    case _: Exception => Stop
  }

  def receive = {
    case async: AsyncContext =>
      val props = Props(new AsyncActor(endpoints)).withDispatcher("akka.http.actor.dispatcher")
      val actor = context.actorOf(props)
      actor ! async
  }
}