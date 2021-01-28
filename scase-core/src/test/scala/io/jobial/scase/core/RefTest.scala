package io.jobial.scase.core

import cats.effect.IO
import cats.effect.concurrent.{MVar, Ref}

import scala.concurrent.ExecutionContext

object RefTest extends App {

  implicit val cs = IO.contextShift(ExecutionContext.global)
  
  
  val result = (for {
    v <- Ref.of[IO, List[Int]](List())
    u <- v.update(1 :: _)
    u <- v.update(2 :: _)
    r <- v.get 
  } yield r).unsafeRunSync()
  
  println(result)
}
