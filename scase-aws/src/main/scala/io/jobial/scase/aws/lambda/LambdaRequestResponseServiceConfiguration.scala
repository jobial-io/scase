package io.jobial.scase.aws.lambda

import cats.effect.{Concurrent, Sync}
import io.jobial.scase.aws.util.AwsContext
import io.jobial.scase.core.{RequestResponseService, RequestResponseServiceConfiguration, RequestResponseServiceState}
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}

case class LambdaRequestResponseServiceConfiguration[REQ: Marshaller, RESP: Unmarshaller](
  functionName: String
)(
  implicit awsContext: AwsContext
) extends RequestResponseServiceConfiguration[REQ, RESP] {

  val serviceName = functionName

  def service[F[_] : Concurrent] =
  // TODO: it could be verified here if the lambda function for the request processor is actually deployed and accessible...
    ExternalService[F, REQ, RESP]()

  def client[F[_] : Concurrent] = LambdaRequestResponseClient[F, REQ, RESP](functionName)
}

case class ExternalService[F[_], REQ, RESP]()(implicit m: Sync[F]) extends RequestResponseService[F, REQ, RESP] {
  // TODO: revisit this - it is a noop at the moment
  override def startService = Sync[F].delay {
    new RequestResponseServiceState[F, REQ] {
      override def stopService = Sync[F].delay(this)
    }
  }
}

