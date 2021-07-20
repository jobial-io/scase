package io.jobial.scase.aws.lambda

import cats.effect.{Concurrent, Sync}
import io.jobial.scase.aws.util.AwsContext
import io.jobial.scase.core.{RequestResponseService, RequestResponseServiceConfiguration, RequestResponseServiceState}
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}

case class LambdaRequestResponseServiceConfiguration[REQ: Marshaller, RESP: Unmarshaller](
  functionName: String
) extends RequestResponseServiceConfiguration[REQ, RESP] {

  val serviceName = functionName

  def client[F[_] : Concurrent] =
    Concurrent[F].delay(LambdaRequestResponseClient[F, REQ, RESP](functionName))
}


