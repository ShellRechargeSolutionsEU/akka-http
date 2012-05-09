package com.thenewmotion.akka.http

import org.specs2.mutable.SpecificationWithJUnit
import akka.actor.ActorSystem

/**
 * @author Yaroslav Klymko
 */
class HttpExtensionSpec extends SpecificationWithJUnit {

  "HttpExtension" should {
    "load on startup" >> {
      println(">>")
      println("system >> ")
      val system = ActorSystem()
      println("system << ")
      println("Extension >> ")
      val extension = HttpExtension(system)
      println("<<")
      todo
    }
  }
}
