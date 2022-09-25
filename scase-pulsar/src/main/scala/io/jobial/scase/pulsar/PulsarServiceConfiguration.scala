package io.jobial.scase.pulsar

import cats.effect.{Concurrent, ContextShift, IO, Timer}
import io.jobial.scase.core.{MessageConsumer, MessageHandler, MessageProducer, ReceiverClient, RequestHandler, RequestResponseClient, ServiceConfiguration}
import cats.implicits._
import io.jobial.scase.core.SenderClient
import io.jobial.scase.core.impl.ConsumerProducerStreamService
import io.jobial.scase.core.impl.ProducerSenderClient
import io.jobial.scase.core.impl.{ConsumerMessageHandlerService, ConsumerProducerRequestResponseClient, ConsumerProducerRequestResponseService, ConsumerReceiverClient, ResponseProducerIdNotFound}
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}
import java.util.UUID.randomUUID
import scala.concurrent.duration._

class PulsarMessageHandlerServiceConfiguration[M: Marshaller : Unmarshaller](
  val serviceName: String,
  requestTopic: String
) extends ServiceConfiguration {

  def service[F[_] : Concurrent](messageHandler: MessageHandler[F, M])
    (
      implicit context: PulsarContext,
      cs: ContextShift[IO]
    ) =
    for {
      consumer <- PulsarConsumer[F, M](requestTopic)
      service = new ConsumerMessageHandlerService(
        consumer,
        messageHandler
      )
    } yield service

  def client[F[_] : Concurrent : Timer](
    implicit context: PulsarContext,
    cs: ContextShift[IO]
  ): F[SenderClient[F, M]] =
    for {
      producer <- PulsarProducer[F, M](requestTopic)
      client <- ProducerSenderClient[F, M](
        producer
      )
    } yield client

}

class PulsarRequestResponseServiceConfiguration[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
  val serviceName: String,
  val requestTopic: String,
  val responseTopicOverride: Option[String],
  val batchingMaxPublishDelay: Duration
)(
  //implicit monitoringPublisher: MonitoringPublisher = noPublisher
  implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
  responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
) extends ServiceConfiguration {

  val responseTopic = responseTopicOverride.getOrElse(s"$requestTopic-response-${randomUUID}")

  def service[F[_] : Concurrent](requestHandler: RequestHandler[F, REQ, RESP])(
    implicit context: PulsarContext,
    cs: ContextShift[IO]
  ) =
    for {
      consumer <- PulsarConsumer[F, REQ](requestTopic)
      service <- ConsumerProducerRequestResponseService[F, REQ, RESP](
        consumer, { responseTopic =>
          responseTopic match {
            case Some(responseTopic) =>
              PulsarProducer[F, Either[Throwable, RESP]](responseTopic).map(p => p: MessageProducer[F, Either[Throwable, RESP]])
            case None =>
              Concurrent[F].raiseError(ResponseProducerIdNotFound("Not found response producer id in request"))
          }
        }: Option[String] => F[MessageProducer[F, Either[Throwable, RESP]]],
        requestHandler
      )
    } yield service

  def client[F[_] : Concurrent : Timer](
    implicit context: PulsarContext,
    cs: ContextShift[IO]
  ): F[RequestResponseClient[F, REQ, RESP]] = {
    for {
      producer <- PulsarProducer[F, REQ](requestTopic)
      consumer <- PulsarConsumer[F, Either[Throwable, RESP]](responseTopic)
      client <- ConsumerProducerRequestResponseClient[F, REQ, RESP](
        consumer,
        () => producer,
        Some(responseTopic)
      )
    } yield client
  }

}

class PulsarStreamServiceConfiguration[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
  val serviceName: String,
  val requestTopic: String,
  val responseTopic: String,
  val batchingMaxPublishDelay: Duration
)(
  //implicit monitoringPublisher: MonitoringPublisher = noPublisher
  implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
  responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
) extends ServiceConfiguration {

  def service[F[_] : Concurrent](requestHandler: RequestHandler[F, REQ, RESP])(
    implicit context: PulsarContext,
    cs: ContextShift[IO]
  ) =
    for {
      consumer <- PulsarConsumer[F, REQ](requestTopic)
      service <- ConsumerProducerRequestResponseService[F, REQ, RESP](
        consumer, { responseTopicFromRequest =>
          PulsarProducer[F, Either[Throwable, RESP]](responseTopicFromRequest.getOrElse(responseTopic)).map(p => p: MessageProducer[F, Either[Throwable, RESP]])
        }: Option[String] => F[MessageProducer[F, Either[Throwable, RESP]]],
        requestHandler
      )
    } yield service

  def senderClient[F[_] : Concurrent : Timer](
    implicit context: PulsarContext,
    cs: ContextShift[IO]
  ): F[SenderClient[F, REQ]] =
    for {
      producer <- PulsarProducer[F, REQ](requestTopic)
      client <- ProducerSenderClient[F, REQ](
        producer
      )
    } yield client

  def receiverClient[F[_] : Concurrent : Timer](
    implicit context: PulsarContext,
    cs: ContextShift[IO]
  ): F[ReceiverClient[F, Either[Throwable, RESP]]] =
    for {
      consumer <- PulsarConsumer[F, Either[Throwable, RESP]](responseTopic)
      client <- ConsumerReceiverClient[F, Either[Throwable, RESP]](
        consumer
      )
    } yield client
  
}

class PulsarStreamServiceWithErrorTopicConfiguration[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
  val serviceName: String,
  val requestTopic: String,
  val responseTopic: String,
  val errorTopic: String,
  val batchingMaxPublishDelay: Duration
)(
  //implicit monitoringPublisher: MonitoringPublisher = noPublisher
  implicit errorMarshaller: Marshaller[Throwable],
  errorUnmarshaller: Unmarshaller[Throwable]
) extends ServiceConfiguration {

  def service[F[_] : Concurrent](requestHandler: RequestHandler[F, REQ, RESP])(
    implicit context: PulsarContext,
    cs: ContextShift[IO]
  ) =
    for {
      consumer <- PulsarConsumer[F, REQ](requestTopic)
      service <- ConsumerProducerStreamService[F, REQ, RESP](
        consumer, { _ =>
          for {
            producer <- PulsarProducer[F, RESP](responseTopic)
          } yield producer
        }: Option[String] => F[MessageProducer[F, RESP]], { _ =>
          for {
            producer <- PulsarProducer[F, Throwable](responseTopic)
          } yield producer
        }: Option[String] => F[MessageProducer[F, Throwable]],
        requestHandler,
        defaultProducerId = None,
        autoCommitRequest = false,
        autoCommitFailedRequest = false
      )
    } yield service

  def senderClient[F[_] : Concurrent : Timer](
    implicit context: PulsarContext,
    cs: ContextShift[IO]
  ): F[SenderClient[F, REQ]] =
    for {
      producer <- PulsarProducer[F, REQ](requestTopic)
      client <- ProducerSenderClient[F, REQ](
        producer
      )
    } yield client

  def responseReceiverClient[F[_] : Concurrent : Timer](
    implicit context: PulsarContext,
    cs: ContextShift[IO]
  ): F[ReceiverClient[F, RESP]] =
    for {
      consumer <- PulsarConsumer[F, RESP](responseTopic)
      client <- ConsumerReceiverClient[F, RESP](
        consumer
      )
    } yield client

  def errorReceiverClient[F[_] : Concurrent : Timer](
    implicit context: PulsarContext,
    cs: ContextShift[IO]
  ): F[ReceiverClient[F, Throwable]] =
    for {
      consumer <- PulsarConsumer[F, Throwable](responseTopic)
      client <- ConsumerReceiverClient[F, Throwable](
        consumer
      )
    } yield client
}


class PulsarMessageSourceServiceConfiguration[M: Unmarshaller](
  sourceTopic: String
) {
  def client[F[_] : Concurrent : Timer](
    implicit context: PulsarContext,
    cs: ContextShift[IO]
  ): F[ReceiverClient[F, M]] =
    for {
      consumer <- PulsarConsumer[F, M](sourceTopic)
      client <- ConsumerReceiverClient[F, M](consumer)
    } yield client
}

class PulsarMessageDestinationServiceConfiguration[M: Marshaller](
  topic: String
) {

  def client[F[_] : Concurrent : Timer](
    implicit context: PulsarContext,
    cs: ContextShift[IO]
  ): F[SenderClient[F, M]] =
    for {
      producer <- PulsarProducer[F, M](topic)
      client <- ProducerSenderClient[F, M](
        producer
      )
    } yield client
}

object PulsarServiceConfiguration {

  def requestResponse[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
    requestTopic: String,
    batchingMaxPublishDelay: Duration = 1.millis
  )(
    //implicit monitoringPublisher: MonitoringPublisher = noPublisher
    implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
    responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
  ): PulsarRequestResponseServiceConfiguration[REQ, RESP] =
    new PulsarRequestResponseServiceConfiguration[REQ, RESP](
      requestTopic,
      requestTopic,
      None,
      batchingMaxPublishDelay
    )

  def stream[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
    requestTopic: String,
    responseTopic: String,
    batchingMaxPublishDelay: Duration
  )(
    //implicit monitoringPublisher: MonitoringPublisher = noPublisher
    implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
    responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
  ): PulsarStreamServiceConfiguration[REQ, RESP] =
    new PulsarStreamServiceConfiguration[REQ, RESP](
      requestTopic,
      requestTopic,
      responseTopic,
      batchingMaxPublishDelay
    )

  def stream[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
    requestTopic: String,
    responseTopic: String
  )(
    //implicit monitoringPublisher: MonitoringPublisher = noPublisher
    implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
    responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
  ): PulsarStreamServiceConfiguration[REQ, RESP] =
    stream(requestTopic, responseTopic, 1.millis)

  def stream[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
    requestTopic: String,
    responseTopic: String,
    errorTopic: String,
    batchingMaxPublishDelay: Duration = 1.millis
  )(
    //implicit monitoringPublisher: MonitoringPublisher = noPublisher
    implicit errorMarshaller: Marshaller[Throwable],
    errorUnmarshaller: Unmarshaller[Throwable]
  ) =
    new PulsarStreamServiceWithErrorTopicConfiguration[REQ, RESP](
      requestTopic,
      requestTopic,
      responseTopic,
      errorTopic,
      batchingMaxPublishDelay
    )

  def handler[M: Marshaller : Unmarshaller](requestTopic: String) =
    new PulsarMessageHandlerServiceConfiguration[M](requestTopic, requestTopic)

  def source[M: Unmarshaller](sourceTopic: String) =
    new PulsarMessageSourceServiceConfiguration(sourceTopic)

  def destination[M: Marshaller](topic: String) =
    new PulsarMessageDestinationServiceConfiguration[M](topic)


}