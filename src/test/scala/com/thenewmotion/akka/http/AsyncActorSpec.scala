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

  abstract class HttpContext extends TestKit(ActorSystem()) with Scope {

    system.actorOf(Props(_ => {
      case msg => testActor ! msg
    }), "endpoints")

    val asyncContext = AsyncContextMock()

    val actorRef = TestFSMRef(new AsyncActor)

    lazy val endpointActor = TestActorRef(new Actor {
      protected def receive = {
        case req: HttpServletRequest => sender ! Complete(DummyCompleting)
      }
    })

    def asyncEventMessage(on: OnEvent) = AsyncEventMessage(new AsyncEvent(asyncContext, new Exception), on)

    def start() = {
      actorRef.stateName mustEqual Idle
      actorRef.stateData mustEqual Empty
      actorRef ! asyncContext
      actorRef.stateName mustEqual AboutToProcess
      actorRef.stateData mustEqual Context(asyncContext, "/test")
    }

    def res = asyncContext.getResponse.asInstanceOf[HttpServletResponse]
  }

  "AsyncActor" should {
    "look for defined endpoint for current request when started" >> new HttpContext {
      start()
      expectMsgType[Find]
      actorRef ! Found(DummyProcessing)
      there was one(asyncContext).complete()
      actorRef.isTerminated must beTrue
    }
    "pass processing scope to actor" >> new HttpContext {
      start()
      expectMsgType[Find]
      actorRef ! Found(endpointActor)
      there was one(asyncContext).complete()
      actorRef.isTerminated must beTrue
    }
    "when no endpoint received within 'endpoint-retrieval-timeout' respond with HTTP 404" >> new HttpContext {
      start()
      expectMsgType[Find]
      actorRef ! FSM.StateTimeout
      there was one(res).setStatus(HttpServletResponse.SC_NOT_FOUND)
      there was one(asyncContext).complete()
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
      start()

      var called = false

      def completion(res: HttpServletResponse): (Boolean => Unit) = {
        called = true
        DummyCallback
      }

      expectMsgType[Find]
      actorRef ! asyncEventMessage(OnTimeout)
      actorRef ! Found(Endpoint(_ => completion))
      actorRef.isTerminated must beTrue
      called must beFalse
    }
    "response if async not completed" >> new HttpContext {
      start()

      var called = false

      def completion(res: HttpServletResponse): (Boolean => Unit) = {
        called = true
        DummyCallback
      }

      expectMsgType[Find]
      actorRef ! Found(Endpoint(_ => completion))
      there was one(asyncContext).complete()
      called must beTrue
    }
    "not response if async already completed" >> new HttpContext {
      start()

      var called = false

      def completion(res: HttpServletResponse): (Boolean => Unit) = {
        called = true
        DummyCallback
      }

      def func(req: HttpServletRequest): (HttpServletResponse => Boolean => Unit) = {
        actorRef ! asyncEventMessage(OnTimeout)
        completion
      }

      expectMsgType[Find]
      actorRef ! Found(func _ )

      there was no(asyncContext).complete()
      called must beFalse
    }
    "response with 'Status Code 500' when exception while processing request" >> new HttpContext {
      start()

      def func(req: HttpServletRequest): (HttpServletResponse => Boolean => Unit) = {
        throw new Exception
      }

      expectMsgType[Find]
      actorRef ! Found(func _)

      there was one(res).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
      there was one(asyncContext).complete()
    }
    "response with 'Status Code 500' when exception while responding" >> new HttpContext {
      start()

      val responseException = (res: HttpServletResponse) => throw new Exception

      expectMsgType[Find]
      actorRef ! Found(Endpoint(_ => responseException))

      there was one(res).sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, null)
      there was one(asyncContext).complete()
    }
    "pass to enpoint true if completed successfully" >> new HttpContext {
      start()

      expectMsgType[Find]
      var result = false
      actorRef ! Found(Endpoint(_ => _ => result = _))

      there was one(asyncContext).complete()
      result must beTrue
    }
    "pass to endpoint false if not completed successfully" >> new HttpContext {
      start()

      expectMsgType[Find]
      asyncContext.complete() throws (new RuntimeException)
      var result = true
      actorRef ! Found(Endpoint(_ => _ => result = _))

      there was one(asyncContext).complete()
      result must beFalse
    }
  }
}