package com.thenewmotion.akka.http

import org.specs2.mutable.SpecificationWithJUnit


/**
 * @author Yaroslav Klymko
 */
class AkkaHttpServletSpec extends SpecificationWithJUnit {

  args(sequential = true)

  lazy val servlet = new AkkaHttpServlet()

  "AkkaHttpServlet" should {


    "create ActorSystem on init" >> {
      println("1")
      servlet._actorSystem must beNone
      servlet.init()
      servlet._actorSystem must beSome
    }

    "create actor for each request" >> {
      println("2")
      val asyncContext = AsyncContextMock()
      val req = asyncContext.getRequest
      val res = asyncContext.getResponse
      //      servlet.
      servlet.service(req, res)

      todo
    }

    "have no memory leaks" >> todo

    "shutdown ActorSystem on destroy" >> {
      println("3")
      servlet._actorSystem must beSome
      val system = servlet._actorSystem.get
      servlet.destroy()
      system.awaitTermination()
      system.isTerminated must beTrue
      servlet._actorSystem must beNone
    }
  }
}
