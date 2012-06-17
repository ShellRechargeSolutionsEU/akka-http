package com.thenewmotion.akka.http

import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import org.specs2.mutable.SpecificationWithJUnit
import javax.servlet.AsyncEvent
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import Listener._
import Async._
import Endpoints._
import akka.testkit.{TestActorRef, TestFSMRef, TestKit}
import akka.actor.{Actor, FSM, ActorSystem, Props}


/**
 * @author Yaroslav Klymko
 */
class AsyncActorSpec extends SpecificationWithJUnit with Mockito {

  abstract class HttpContext() extends TestKit(ActorSystem()) with Scope {

    val asyncContext = AsyncContextMock()

    var actorRef: TestFSMRef[State, Data, AsyncActor] = null

    lazy val endpointActor = TestActorRef(new Actor {
      protected def receive = {
        case req: HttpServletRequest => sender ! Complete(DummyCompleting)
      }
    })

    def asyncEventMessage(on: OnEvent) = AsyncEventMessage(new AsyncEvent(asyncContext, new Exception), on)

    def start(e: Endpoint) {
      start(Some(e))
    }

    def start(e: Option[Endpoint] = None) {
      val endpoints = new EndpointFinder {
        def find(url: String) = e
      }

      actorRef = TestFSMRef(new AsyncActor(endpoints))
      actorRef.stateName mustEqual AboutToProcess
      actorRef.stateData mustEqual Empty
      actorRef ! asyncContext
    }

    def res = asyncContext.getResponse.asInstanceOf[HttpServletResponse]
  }

  "AsyncActor" should {
    "look for defined endpoint for current request when started" >> new HttpContext {
      start(DummyProcessing)
      there was one(asyncContext).complete()
      actorRef.isTerminated must beTrue
    }
    "pass processing scope to actor" >> new HttpContext {
      start(endpointActor)
      there was one(asyncContext).complete()
      actorRef.isTerminated must beTrue
    }
    "stop self on Error event" >> new HttpContext {
      start()
      actorRef ! asyncEventMessage(OnError)
      actorRef.isTerminated must beTrue
    }
    "stop self on Complete event" >> new HttpContext {
      start()
      actorRef ! asyncEventMessage(OnComplete)
      actorRef.isTerminated must beTrue
    }
    "stop self on Timeout event" >> new HttpContext {
      var called = false

      def completion(res: HttpServletResponse): (Boolean => Unit) = {
        called = true
        DummyCallback
      }

      def processing(req: HttpServletRequest): Completing = {
        actorRef ! asyncEventMessage(OnTimeout)
        completion
      }

      start(processing _)

      actorRef.isTerminated must beTrue
      called must beFalse
    }
    "response if async not completed" >> new HttpContext {
      var called = false

      def completion(res: HttpServletResponse): (Boolean => Unit) = {
        called = true
        DummyCallback
      }

      start(Endpoint(_ => completion))
      there was one(asyncContext).complete()
      called must beTrue
    }
    "not response if async already completed" >> new HttpContext {
      var called = false

      def completion(res: HttpServletResponse): (Boolean => Unit) = {
        called = true
        DummyCallback
      }

      def func(req: HttpServletRequest): (HttpServletResponse => Boolean => Unit) = {
        actorRef ! asyncEventMessage(OnTimeout)
        completion
      }

      start(func _)
      there was no(asyncContext).complete()
      called must beFalse
    }
    "response with 404 when no endpoint found" >> new HttpContext {
      start()
      there was one(res).setStatus(HttpServletResponse.SC_NOT_FOUND)
    }
    "response with 'Status Code 500' when exception while processing request" >> new HttpContext {
      def func(req: HttpServletRequest): (HttpServletResponse => Boolean => Unit) = {
        throw new Exception
      }

      start(func _)
      there was one(res).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
      there was one(asyncContext).complete()
    }
    "response with 'Status Code 500' when exception while responding" >> new HttpContext {
      val responseException = (res: HttpServletResponse) => throw new Exception

      start(Endpoint(_ => responseException))

      there was one(res).sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, null)
      there was one(asyncContext).complete()
    }
    "pass to enpoint true if completed successfully" >> new HttpContext {
      var result = false
      start(Endpoint(_ => _ => result = _))
      there was one(asyncContext).complete()
      result must beTrue
    }
    "pass to endpoint false if not completed successfully" >> new HttpContext {
      asyncContext.complete() throws (new RuntimeException)
      var result = true
      start(Endpoint(_ => _ => result = _))

      there was one(asyncContext).complete()
      result must beFalse
    }
  }
}