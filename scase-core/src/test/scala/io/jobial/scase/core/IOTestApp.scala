package io.jobial.scase.core

import cats.effect.IO

object IOTestApp extends App {

  val a: IO[Unit] = IO.async(x => println("hello"))
  
  println(a.unsafeToFuture())
  println(a.unsafeToFuture())
  
  val b = IO(println("bello"))
  //IO.cancelable()

  Thread.sleep(10000)
}
