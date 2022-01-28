package io.jobial.scase.aws.sqs

import cats.effect.{Concurrent, ContextShift, IO, Timer}
import cats.implicits._
import io.jobial.scase.aws.client.AwsContext
import io.jobial.scase.core.impl.{ConsumerMessageHandlerService, ProducerSenderClient}
import io.jobial.scase.core.{MessageHandler, SenderClient, ServiceConfiguration}
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}

case class SqsOneWayServiceConfiguration[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
  serviceName: String,
  requestQueueName: String,
  cleanup: Boolean
)(
  //implicit monitoringPublisher: MonitoringPublisher = noPublisher
  implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
  responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
) extends ServiceConfiguration {

  val requestQueueUrl = requestQueueName

  def service[F[_] : Concurrent](messageHandler: MessageHandler[F, REQ])(
    implicit awsContext: AwsContext = AwsContext(),
    cs: ContextShift[IO]
  ) = for {
    requestConsumer <- SqsConsumer[F, REQ](requestQueueUrl, cleanup = false)
    service = new ConsumerMessageHandlerService[F, REQ](
      requestConsumer,
      messageHandler
    )
  } yield service

  def client[F[_] : Concurrent : Timer](
    implicit awsContext: AwsContext = AwsContext(),
    cs: ContextShift[IO]
  ): F[SenderClient[F, REQ]] = {
    for {
      producer <- SqsProducer[F, REQ](requestQueueUrl)
      client = ProducerSenderClient[F, REQ](producer)
    } yield client
  }

}

