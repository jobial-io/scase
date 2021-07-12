package io.jobial.scase.aws.lambda

import cats.effect.{Concurrent, IO}
import io.jobial.scase.core.RequestProcessor

import scala.concurrent.ExecutionContext

trait IOLambdaRequestHandler[REQ, RESP] extends LambdaRequestHandler[IO, REQ, RESP] {
  this: RequestProcessor[IO, REQ, RESP] =>

  implicit val cs = IO.contextShift(ExecutionContext.global)

  implicit val concurrent = Concurrent[IO]
}