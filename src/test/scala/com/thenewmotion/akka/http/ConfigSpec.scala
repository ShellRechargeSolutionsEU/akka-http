package com.thenewmotion.akka.http

import org.specs2.mutable.SpecificationWithJUnit
import akka.actor.ActorSystem

class ConfigSpec extends SpecificationWithJUnit {

  "The default configuration file 'reference.conf'" should {
    "contain properties for akka-http that are used in code with their correct defaults" >> {
      val config = ActorSystem().settings.config
      import config._
      getString("akka.http.expired-header-name") mustEqual "Async-Timeout"
      getString("akka.http.expired-header-value") mustEqual "expired"
      getString("akka.http.system-name") mustEqual "http"
      getString("akka.http.endpoints-path") mustEqual "endpoints"
      getLong("akka.http.timeout") mustEqual 1000L
      getLong("akka.http.endpoint-retrieval-timeout") mustEqual 500L
    }
  }
}