package com.thenewmotion.akka.http

import org.specs2.mutable.SpecificationWithJUnit
import akka.actor.ActorSystem

class ConfigSpec extends SpecificationWithJUnit {

  "The default configuration file 'reference.conf'" should {
    "contain properties for akka-http that are used in code with their correct defaults" >> {
      val config = ActorSystem().settings.config
      import config._
      getBoolean("akka.http.connection-close") mustEqual true
      getString("akka.http.expired-header-name") mustEqual "Async-Timeout"
      getString("akka.http.expired-header-value") mustEqual "expired"
      getBoolean("akka.http.root-actor-builtin") mustEqual true
      getString("akka.http.root-actor-path") mustEqual "/http/root"
      getLong("akka.http.timeout") mustEqual 1000L
    }
  }
}