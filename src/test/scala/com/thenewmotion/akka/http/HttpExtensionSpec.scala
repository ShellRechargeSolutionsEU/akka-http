package com.thenewmotion.akka.http

import org.specs2.mutable.SpecificationWithJUnit
import akka.actor.ActorSystem

/**
 * @author Yaroslav Klymko
 */
class HttpExtensionSpec extends SpecificationWithJUnit {

  "HttpExtension" should {
    "have properties corresponding to .conf" >> {
      val extension = HttpExtension(ActorSystem())
      import extension._
      AsyncTimeout mustEqual 1000L
      ExpiredHeader mustEqual "Async-Timeout" -> "expired"
      SupervisorPath mustEqual "async"
      LogConfigOnInit must beFalse
    }
  }
}
