package io.jobial.scase.core

import cats.effect.IO
import cats.effect.concurrent.{Deferred, MVar}

import scala.concurrent.ExecutionContext

object MVarTest extends App {

  implicit val cs = IO.contextShift(ExecutionContext.global)
  
  
  val result = (for {
    v <- MVar[IO].empty[Int]
    r <- v.put(1)
    r <- v.take
  } yield r).unsafeRunSync()
  
  println(result)
}
