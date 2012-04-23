package com.thenewmotion.akka.http

import org.specs2.mock.Mockito
import org.specs2.mutable._


/**
 * @author Yaroslav Klymko
 */
class AkkaHttpServletSpec extends SpecificationWithJUnit {

  args(sequential = true)

  lazy val servlet = new AkkaHttpServlet()

  "AkkaHttpServlet" should {


    "creates ActorSystem on init" >> {
      servlet.actorSystem must beNone
      servlet.init()
      servlet.actorSystem must beSome
    }

    "creates actor for each request" >> {
      val asyncContext = AsyncContextMock()
      val req = asyncContext.getRequest
      val res = asyncContext.getResponse

      servlet.service(req, res)

    }

    "shutdown ActorSystem on destroy" >> {
      servlet.actorSystem must beSome
      val system = servlet.actorSystem.get
      servlet.destroy()
      system.awaitTermination()
      system.isTerminated must beTrue
      servlet.actorSystem must beNone
    }
  }
}
