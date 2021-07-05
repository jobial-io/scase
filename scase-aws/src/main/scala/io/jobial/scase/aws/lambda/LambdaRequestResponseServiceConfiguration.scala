package io.jobial.scase.aws.lambda

import cats.effect.{ContextShift, IO}
import io.jobial.scase.aws.util.AwsContext
import io.jobial.scase.core.{RequestResponseService, RequestResponseServiceConfiguration, RequestResponseServiceState}
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}

case class LambdaRequestResponseServiceConfiguration[REQ: Marshaller, RESP: Unmarshaller](
  functionName: String
)(
  implicit awsContext: AwsContext,
  cs: ContextShift[IO]
) extends RequestResponseServiceConfiguration[REQ, RESP] {

  val serviceName = functionName

  def service =
  // TODO: it could be verified here if the lambda function for the request processor is actually deployed...
    ExternalService[REQ, RESP]()

  def client = LambdaRequestResponseClient[REQ, RESP](functionName)
}

case class ExternalService[REQ, RESP]() extends RequestResponseService[REQ, RESP] {
  // TODO: revisit this - it is a noop at the moment
  override def startService = IO {
    new RequestResponseServiceState[REQ] {
      override def stopService = IO(this)
    }
  }
}

