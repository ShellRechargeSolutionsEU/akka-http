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
import akka.actor.{Actor, ActorSystem}


/**
 * @author Yaroslav Klymko
 */
class AsyncActorSpec extends SpecificationWithJUnit with Mockito {

  abstract class HttpContext() extends TestKit(ActorSystem()) with Scope {

    val asyncContext = AsyncContextMock()

    var actorRef: TestFSMRef[State, Data, AsyncActor] = null

    lazy val endpointActor = TestActorRef(new Actor {
      def receive = {
        case req: HttpServletRequest => sender ! Complete(FutureResponse{_ => })
      }
    })

    def asyncEventMessage(on: OnEvent) = AsyncEventMessage(new AsyncEvent(asyncContext, new Exception), on)

    def start(e: Endpoint) {
      start(Some(e))
    }

    def start(e: Option[Endpoint] = None) {
      val provider = new Provider {
        def apply(v1: String) = e.get
        def isDefinedAt(x: String) = e.isDefined
      }

      actorRef = TestFSMRef(new AsyncActor(provider,""))
      actorRef.stateName mustEqual AboutToProcess
      actorRef.stateData mustEqual NoData
      actorRef ! asyncContext
    }

    def res = asyncContext.getResponse.asInstanceOf[HttpServletResponse]
  }

  "AsyncActor" should {
    "look for defined endpoint for current request when started" >> new HttpContext {
      start()
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

      val endpoint = RequestResponse {
        req =>
          actorRef ! asyncEventMessage(OnTimeout)
          FutureResponse(_ => called = true)
      }

      start(endpoint)

      actorRef.isTerminated must beTrue
      called must beFalse
    }

    "respond if async not completed" >> new HttpContext {
      var called = false
      start(Endpoint(_ => FutureResponse(_ => called = true)))
      there was one(asyncContext).complete()
      called must beTrue
    }

    "not respond if async already completed" >> new HttpContext {
      var called = false

      def func(req: HttpServletRequest) = {
        actorRef ! asyncEventMessage(OnTimeout)
        FutureResponse {
          _ => called = true
        }
      }

      start(func _)
      there was no(asyncContext).complete()
      called must beFalse
    }

    "respond with 404 when no endpoint found" >> new HttpContext {
      start()
      there was one(res).setStatus(HttpServletResponse.SC_NOT_FOUND)
    }

    "respond with 'Status Code 500' when exception while processing request" >> new HttpContext {
      start(RequestResponse(req => throw new Exception))
      there was one(res).sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
      there was one(asyncContext).complete()
    }

    "respond with 'Status Code 500' when exception while responding" >> new HttpContext {
      start(RequestResponse(FutureResponse {
        res => throw new Exception
      }))
      there was one(res).sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
      there was one(asyncContext).complete()
    }

    "call `onComplete` with None if completed successfully" >> new HttpContext {
      var result = false
      val future = new FutureResponse {
        def apply(res: HttpServletResponse) {}

        def onComplete = {
          case None => result = true
        }
      }
      start(RequestResponse(future))
      there was one(asyncContext).complete()
      result must beTrue
    }
  }
}