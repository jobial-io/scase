package io.jobial.scase.core

import cats.effect.IO
import org.scalatest.compatible.Assertion
import org.scalatest.flatspec.AsyncFlatSpec

import scala.concurrent.ExecutionContext

trait ScaseTestHelper {
  this: AsyncFlatSpec =>

  implicit val cs = IO.contextShift(ExecutionContext.global)

  implicit def runIOResult(r: IO[Assertion]) = r.unsafeToFuture

  implicit def fromEitherResult(r: Either[Throwable, Assertion]) = runIOResult(IO.fromEither(r))
}
