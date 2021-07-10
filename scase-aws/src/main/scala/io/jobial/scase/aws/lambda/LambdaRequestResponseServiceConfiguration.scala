package io.jobial.scase.aws.lambda

import cats.Monad
import cats.effect.{Concurrent, ContextShift, IO, Sync}
import io.jobial.scase.aws.util.AwsContext
import io.jobial.scase.core.{RequestResponseService, RequestResponseServiceConfiguration, RequestResponseServiceState}
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}

case class LambdaRequestResponseServiceConfiguration[F[_], REQ: Marshaller, RESP: Unmarshaller](
  functionName: String
)(
  implicit awsContext: AwsContext,
  val concurrent: Concurrent[F]
) extends RequestResponseServiceConfiguration[REQ, RESP] {

  val serviceName = functionName

  def service =
  // TODO: it could be verified here if the lambda function for the request processor is actually deployed...
    ExternalService[F, REQ, RESP]()

  def client = LambdaRequestResponseClient[F, REQ, RESP](functionName)
}

case class ExternalService[F[_], REQ, RESP]()(implicit m: Sync[F]) extends RequestResponseService[F, REQ, RESP] {
  // TODO: revisit this - it is a noop at the moment
  override def startService =  Sync[F].delay {
    new RequestResponseServiceState[F, REQ] {
      override def stopService = Sync[F].delay(this)
    }
  }
}

