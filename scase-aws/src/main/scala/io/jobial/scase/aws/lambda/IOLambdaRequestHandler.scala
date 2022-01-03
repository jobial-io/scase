package io.jobial.scase.aws.lambda

import cats.effect.{Concurrent, IO}
import io.jobial.scase.core.RequestHandler

import scala.concurrent.ExecutionContext

abstract class IOLambdaRequestHandler[REQ, RESP](serviceConfiguration: LambdaRequestResponseServiceConfiguration[REQ, RESP])
  extends LambdaRequestHandler[IO, REQ, RESP](serviceConfiguration) {
  this: RequestHandler[IO, REQ, RESP] =>

  implicit val cs = IO.contextShift(ExecutionContext.global)

  implicit val concurrent = Concurrent[IO]
}