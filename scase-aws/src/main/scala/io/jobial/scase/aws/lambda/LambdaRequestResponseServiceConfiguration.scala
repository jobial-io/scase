package io.jobial.scase.aws.lambda

import cats.effect.Concurrent
import io.jobial.scase.aws.client.AwsContext
import io.jobial.scase.core.RequestResponseServiceConfiguration
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}

case class LambdaRequestResponseServiceConfiguration[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
  functionName: String
) extends RequestResponseServiceConfiguration[REQ, RESP] {

  // TODO: overload constructor for this
  val serviceName = functionName

  def client[F[_] : Concurrent](implicit awsContext: AwsContext = AwsContext()) =
    Concurrent[F].delay(LambdaRequestResponseClient[F, REQ, RESP](functionName))

  val requestMarshaller = Marshaller[REQ]
  
  val requestUnmarshaller = Unmarshaller[REQ]

  val responseMarshaller = Marshaller[RESP]

  val responseUnmarshaller = Unmarshaller[RESP]
}


