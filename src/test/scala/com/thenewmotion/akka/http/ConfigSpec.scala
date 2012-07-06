package com.thenewmotion.akka.http

import org.specs2.mutable.SpecificationWithJUnit
import akka.actor.ActorSystem

class ConfigSpec extends SpecificationWithJUnit {

  "The default configuration file 'reference.conf'" should {
    "contain properties for akka.http that are used in code with their correct defaults" >> {
      val config = ActorSystem().settings.config.getConfig("akka.http")
      import config._
      getString("expired-header-name") mustEqual "Async-Timeout"
      getString("expired-header-value") mustEqual "expired"
      getString("system-name") mustEqual "http"
      getString("supervisor-path") mustEqual "async"
      getLong("timeout") mustEqual 1000L
      getBoolean("log-http-config-on-init") must beFalse
    }
  }
}