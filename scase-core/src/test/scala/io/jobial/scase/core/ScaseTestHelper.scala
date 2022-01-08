package io.jobial.scase.core

import cats.effect.IO
import org.scalatest.compatible.Assertion
import org.scalatest.flatspec.AsyncFlatSpec

import java.util.concurrent.Executors.newCachedThreadPool
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.fromExecutor

trait ScaseTestHelper {
  this: AsyncFlatSpec =>

  implicit val cs = IO.contextShift(fromExecutor(newCachedThreadPool))

  implicit val timer = IO.timer(ExecutionContext.global)

  implicit def runIOResult(r: IO[Assertion]) = r.unsafeToFuture

  implicit def fromEitherResult(r: Either[Throwable, Assertion]) = runIOResult(IO.fromEither(r))
}
