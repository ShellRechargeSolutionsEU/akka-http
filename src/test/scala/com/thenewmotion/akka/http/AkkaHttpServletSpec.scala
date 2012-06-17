package com.thenewmotion.akka.http

import org.specs2.mutable.SpecificationWithJUnit


/**
 * @author Yaroslav Klymko
 */
class AkkaHttpServletSpec extends SpecificationWithJUnit {

  args(sequential = true)

  lazy val servlet = new AkkaHttpServlet

  "AkkaHttpServlet" should {

    "create ActorSystem and EndpointAgent on init" >> {
      servlet._httpSystem must beNone
      servlet.init()
      servlet._httpSystem must beSome
    }

    "create actor for each request" >> {
      val limit = 100
      (0 to limit).foreach {
        _ =>
          val async = AsyncContextMock()
          servlet.service(async.getRequest, async.getResponse)
      }
      success
    }

    "shutdown ActorSystem on destroy" >> {
      servlet._httpSystem must beSome
      val (system, _) = servlet._httpSystem.get
      servlet.destroy()
      system.awaitTermination()
      system.isTerminated must beTrue
      servlet._httpSystem must beNone
    }
  }
}
