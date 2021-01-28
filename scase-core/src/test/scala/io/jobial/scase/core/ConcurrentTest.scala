package io.jobial.scase.core

import cats.effect.IO
import cats.effect.concurrent.Deferred

import scala.concurrent.ExecutionContext

object ConcurrentTest extends App {

  implicit val cs = IO.contextShift(ExecutionContext.global)
  
  val result = (for {
    d <- Deferred[IO, Int]
    r <- d.complete(1)
    //r <- d.complete(1)
    r <- d.get
  } yield r).unsafeRunSync()
  
  println(result)
}
