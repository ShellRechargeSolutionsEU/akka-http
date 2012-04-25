package com.thenewmotion.akka.http

import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import javax.servlet.AsyncEvent
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import Listener._
import AsyncActor._
import EndpointsActor._
import org.specs2.mutable.SpecificationWithJUnit
import akka.testkit.{TestKit, TestActorRef}
import akka.actor.{ActorSystem, Props}


/**
 * @author Yaroslav Klymko
 */
//TODO maybe it's FSM
class AsyncActorSpec extends SpecificationWithJUnit with Mockito {

  abstract class HttpContext extends TestKit(ActorSystem()) with Scope {

    system.actorOf(Props(_ => {
      case msg => testActor ! msg
    }), "endpoints")

    val asyncContext = AsyncContextMock()

    val actorRef = TestActorRef(new AsyncActor(asyncContext))

    def asyncEventMessage(on: OnEvent) = AsyncEventMessage(new AsyncEvent(asyncContext, new Exception), on)

    def res = asyncContext.getResponse.asInstanceOf[HttpServletResponse]
  }

  "AsyncActor" should {
    "look for defined endpoint for current request when started" >> new HttpContext {
      actorRef ! Start
      expectMsgType[Find]
      actorRef ! Found(_ => _ => _ => Unit)
      there was one(asyncContext).complete()
      actorRef.isTerminated must beTrue
    }
    "stop self on Error event" >> new HttpContext {
      actorRef ! Start
      actorRef ! asyncEventMessage(OnError)
      actorRef.isTerminated must beTrue
    }
    "stop self on Complete event" >> new HttpContext {
      actorRef ! Start
      actorRef ! asyncEventMessage(OnComplete)
      actorRef.isTerminated must beTrue
    }
    "stop self on Timeout event" >> new HttpContext {
      actorRef ! Start

      var called = false

      def completion(res: HttpServletResponse): (Boolean => Unit) = {
        called = true
        (b: Boolean) => ()
      }

      expectMsgType[Find]
      actorRef ! asyncEventMessage(OnTimeout)
      actorRef ! Found(_ => completion)
      actorRef.isTerminated must beTrue
      called must beFalse
    }
    "response if async not completed" >> new HttpContext {
      actorRef ! Start

      var called = false

      def completion(res: HttpServletResponse): (Boolean => Unit) = {
        called = true
        (b: Boolean) => ()
      }

      expectMsgType[Find]
      actorRef ! Found(_ => completion)
      there was one(asyncContext).complete()
      called must beTrue
    }
    "not response if async already completed" >> new HttpContext {
      actorRef ! Start

      var called = false

      def completion(res: HttpServletResponse): (Boolean => Unit) = {
        called = true
        (b: Boolean) => ()
      }

      def func(req: HttpServletRequest): (HttpServletResponse => Boolean => Unit) = {
        actorRef ! asyncEventMessage(OnTimeout)
        completion
      }

      expectMsgType[Find]
      actorRef ! Found(func)

      there was no(asyncContext).complete()
      called must beFalse
    }
    "response with 'Status Code 500' when exception while processing request" >> new HttpContext {
      actorRef ! Start

      def func(req: HttpServletRequest): (HttpServletResponse => Boolean => Unit) = {
        throw new Exception
      }

      expectMsgType[Find]
      actorRef ! Found(func)

      there was one(res).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
      there was one(asyncContext).complete()
    }
    "response with 'Status Code 500' when exception while responding" >> new HttpContext {
      actorRef ! Start

      def func(res: HttpServletResponse): (Boolean => Unit) = {
        throw new Exception
      }

      expectMsgType[Find]
      actorRef ! Found(_ => func)

      there was one(res).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
      there was one(asyncContext).complete()
    }
    "pass to enpoint true if completed successfully" >> new HttpContext {
      actorRef ! Start

      expectMsgType[Find]
      var result = false
      actorRef ! Found(_ => _ => result = _)

      there was one(asyncContext).complete()
      result must beTrue
    }
    "pass to endpoint false if not completed successfully" >> new HttpContext {
      actorRef ! Start

      expectMsgType[Find]
      asyncContext.complete() throws (new RuntimeException)
      var result = true
      actorRef ! Found(_ => _ => result = _)

      there was one(asyncContext).complete()
      result must beFalse
    }
  }
}
