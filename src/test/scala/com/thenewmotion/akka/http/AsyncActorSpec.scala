package com.thenewmotion.akka.http

import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import org.specs2.mutable.SpecificationWithJUnit
import akka.testkit.{TestProbe, TestActorRef}
import akka.util.DurationInt
import javax.servlet.AsyncEvent
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import Listener._
import AsyncActor._
import EndpointsActor._


/**
 * @author Yaroslav Klymko
 */
class AsyncActorSpec extends SpecificationWithJUnit with Mockito {

  abstract class HttpContext extends Scope {

    implicit val system = HttpSystem()
    val probe = TestProbe()

    val asyncContext = AsyncContextMock()
    val actorRef = TestActorRef(new AsyncActor(asyncContext) {
      override def endpointsActor = probe.ref
    })

    def test()

    def apply() {
      try test()
      finally system.shutdown()
    }

    apply()

    def expectFindEndpoint() {
      probe.within(new DurationInt(200).millis) {
        probe.expectMsgType[Find]
      }
    }

    def asyncEventMessage(on: OnEvent) = AsyncEventMessage(new AsyncEvent(asyncContext, new Exception), on)

    def res = asyncContext.getResponse.asInstanceOf[HttpServletResponse]
  }

  "AsyncActor" should {
    "look for defined endpoint for current request when started" >> new HttpContext {
      def test() {
        actorRef ! Start
        expectFindEndpoint()
        probe.sender ! Found(_ => _ => _ => Unit)
        there was one(asyncContext).complete()
        actorRef.isTerminated must beTrue
      }
    }
    "stop self on Error event" >> new HttpContext {
      def test() {
        actorRef ! Start
        actorRef ! asyncEventMessage(OnError)
        actorRef.isTerminated must beTrue
      }
    }
    "stop self on Complete event" >> new HttpContext {
      def test() {
        actorRef ! Start
        actorRef ! asyncEventMessage(OnComplete)
        actorRef.isTerminated must beTrue
      }
    }
    "stop self on Timeout event" >> new HttpContext {
      def test() {
        actorRef ! Start
        actorRef ! asyncEventMessage(OnTimeout)
        actorRef.isTerminated must beTrue
      }
    }
    "response if async not completed" >> new HttpContext {
      def test() {
        actorRef ! Start

        var called = false
        def completion(res: HttpServletResponse): (Boolean => Unit) = {
          called = true
          (b: Boolean) => ()
        }

        expectFindEndpoint()
        probe.sender ! Found(_ => completion)
        there was one(asyncContext).complete()
        called must beTrue
      }
    }
    "not response if async already completed" >> new HttpContext {
      def test() {
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

        expectFindEndpoint()
        probe.sender ! Found(func)

        there was no(asyncContext).complete()
        called must beFalse
      }
    }
    "response with 'Status Code 500' when exception while processing request" >> new HttpContext {
      def test() {
        actorRef ! Start

        def func(req: HttpServletRequest): (HttpServletResponse => Boolean => Unit) = {
          throw new Exception
        }

        expectFindEndpoint()
        probe.sender ! Found(func)

        there was one(res).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
        there was one(asyncContext).complete()
      }
    }
    "response with 'Status Code 500' when exception while responding" >> new HttpContext {
      def test() {
        actorRef ! Start

        def func(res: HttpServletResponse): (Boolean => Unit) = {
          throw new Exception
        }

        expectFindEndpoint()
        probe.sender ! Found(_ => func)

        there was one(res).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
        there was one(asyncContext).complete()
      }
    }
    "pass to enpoint true if completed successfully" >> new HttpContext {
      def test() {
        actorRef ! Start

        expectFindEndpoint()
        var result = false
        probe.sender ! Found(_ => _ => result = _)

        there was one(asyncContext).complete()
        result must beTrue
      }
    }
    "pass to endpoint false if not completed successfully" >> new HttpContext {
      def test() {
        actorRef ! Start

        expectFindEndpoint()
        asyncContext.complete() throws (new RuntimeException)
        var result = true
        probe.sender ! Found(_ => _ => result = _)

        there was one(asyncContext).complete()
        result must beFalse
      }
    }
  }
}
