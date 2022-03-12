package io.jobial.scase.aws.sqs

import cats.effect.{Concurrent, ContextShift, IO, Timer}
import cats.implicits._
import io.jobial.scase.aws.client.AwsContext
import io.jobial.scase.core.impl.{ConsumerMessageHandlerService, ConsumerProducerRequestResponseClient, ConsumerProducerRequestResponseService, ProducerSenderClient, ResponseProducerIdNotFound}
import io.jobial.scase.core.{MessageHandler, MessageProducer, RequestHandler, RequestResponseClient, SenderClient, ServiceConfiguration}
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}
import io.jobial.scase.util.Hash.uuid

case class SqsRequestResponseServiceConfiguration[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
  serviceName: String,
  requestQueueName: String,
  responseQueueName: Option[String],
  cleanup: Boolean
)(
  //implicit monitoringPublisher: MonitoringPublisher = noPublisher
  implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
  responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
) extends ServiceConfiguration {

  val requestQueueUrl = requestQueueName

  val responseQueueUrl = responseQueueName.getOrElse(s"$requestQueueName-response-${uuid(8)}")

  def service[F[_] : Concurrent](requestHandler: RequestHandler[F, REQ, RESP])(
    implicit awsContext: AwsContext = AwsContext(),
    cs: ContextShift[IO]
  ) = for {
    requestConsumer <- SqsConsumer[F, REQ](requestQueueUrl, cleanup = false)
    service <- ConsumerProducerRequestResponseService[F, REQ, RESP](
      requestConsumer, { responseQueueUrl =>
        responseQueueUrl match {
          case Some(responseQueueUrl) =>
            for {
              responseProducer <- SqsProducer[F, Either[Throwable, RESP]](responseQueueUrl, cleanup = true)
            } yield responseProducer
          case None =>
            Concurrent[F].raiseError(ResponseProducerIdNotFound("Not found response producer id in request"))
        }
      }: Option[String] => F[MessageProducer[F, Either[Throwable, RESP]]],
      requestHandler
    )
  } yield service

  def client[F[_] : Concurrent : Timer](
    implicit awsContext: AwsContext = AwsContext(),
    cs: ContextShift[IO]
  ): F[RequestResponseClient[F, REQ, RESP]] = {
    for {
      consumer <- SqsConsumer[F, Either[Throwable, RESP]](responseQueueUrl)
      producer <- SqsProducer[F, REQ](requestQueueUrl)
      client <- ConsumerProducerRequestResponseClient[F, REQ, RESP](
        consumer,
        () => producer,
        Some(responseQueueUrl)
      )
    } yield client
  }

  def senderClient[F[_] : Concurrent : Timer](
    implicit awsContext: AwsContext = AwsContext(),
    cs: ContextShift[IO]
  ): F[SenderClient[F, REQ]] = {
    for {
      producer <- SqsProducer[F, REQ](requestQueueUrl)
      client <- ProducerSenderClient[F, REQ](producer)
    } yield client
  }

}

class SqsMessageHandlerServiceConfiguration[REQ: Marshaller : Unmarshaller](
  val serviceName: String,
  requestQueueName: String,
  cleanup: Boolean
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

  def client[F[_] : Concurrent](
    implicit awsContext: AwsContext = AwsContext(),
    cs: ContextShift[IO]
  ): F[SenderClient[F, REQ]] = {
    for {
      producer <- SqsProducer[F, REQ](requestQueueUrl)
      client <- ProducerSenderClient[F, REQ](producer)
    } yield client
  }

}

object SqsServiceConfiguration {

  def requestResponse[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
    requestQueueName: String
  )(
    //implicit monitoringPublisher: MonitoringPublisher = noPublisher
    implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
    responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
  ): SqsRequestResponseServiceConfiguration[REQ, RESP] =
    SqsRequestResponseServiceConfiguration[REQ, RESP](requestQueueName, requestQueueName, None, false)

  def stream[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
    requestQueueName: String,
    responseQueueName: String
  )(
    //implicit monitoringPublisher: MonitoringPublisher = noPublisher
    implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
    responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
  ) =
    SqsRequestResponseServiceConfiguration[REQ, RESP](requestQueueName, requestQueueName, Some(responseQueueName), false)

  def handler[REQ: Marshaller : Unmarshaller](
    requestQueueName: String
  ) = new SqsMessageHandlerServiceConfiguration[REQ](requestQueueName, requestQueueName, false)
}