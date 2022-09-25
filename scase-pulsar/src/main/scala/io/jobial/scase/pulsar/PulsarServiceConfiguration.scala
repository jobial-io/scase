package io.jobial.scase.pulsar

import cats.effect.Concurrent
import cats.effect.ContextShift
import cats.effect.IO
import cats.effect.Timer
import cats.implicits._
import io.jobial.scase.core.SenderClient
import io.jobial.scase.core.impl.ConsumerProducerStreamService
import io.jobial.scase.core.impl.ProducerSenderClient
import io.jobial.scase.core.impl.ConsumerMessageHandlerService
import io.jobial.scase.core.impl.ConsumerProducerRequestResponseClient
import io.jobial.scase.core.impl.ConsumerProducerRequestResponseService
import io.jobial.scase.core.impl.ConsumerReceiverClient
import io.jobial.scase.core.impl.ResponseProducerIdNotFound
import io.jobial.scase.core.MessageHandler
import io.jobial.scase.core.MessageProducer
import io.jobial.scase.core.ReceiverClient
import io.jobial.scase.core.RequestHandler
import io.jobial.scase.core.RequestResponseClient
import io.jobial.scase.core.ServiceConfiguration
import io.jobial.scase.marshalling.Marshaller
import io.jobial.scase.marshalling.Unmarshaller
import io.jobial.scase.pulsar.PulsarServiceConfiguration.destination
import io.jobial.scase.pulsar.PulsarServiceConfiguration.requestResponse
import io.jobial.scase.pulsar.PulsarServiceConfiguration.source
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
  ) = destination[M](requestTopic).client[F]

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

  def service[F[_] : Concurrent](requestHandler: RequestHandler[F, REQ, RESP])(
    implicit context: PulsarContext,
    cs: ContextShift[IO]
  ) =
    for {
      consumer <- PulsarConsumer[F, REQ](requestTopic)
      service <- ConsumerProducerRequestResponseService[F, REQ, RESP](
        consumer, { responseTopicFromMessage =>
          responseTopicOverride.orElse(responseTopicFromMessage) match {
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
      responseTopic = responseTopicOverride.getOrElse(s"$requestTopic-response-${randomUUID}")
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
  ) = requestResponse[REQ, RESP](requestTopic, Some(responseTopic)).service(requestHandler)

  def senderClient[F[_] : Concurrent : Timer](
    implicit context: PulsarContext,
    cs: ContextShift[IO]
  ) = destination[REQ](requestTopic).client[F]

  def receiverClient[F[_] : Concurrent : Timer](
    implicit context: PulsarContext,
    cs: ContextShift[IO]
  ) = source[Either[Throwable, RESP]](responseTopic).client[F]

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
  ) = destination[REQ](requestTopic).client[F]

  def responseReceiverClient[F[_] : Concurrent : Timer](
    implicit context: PulsarContext,
    cs: ContextShift[IO]
  ) = source[RESP](responseTopic).client[F]

  def errorReceiverClient[F[_] : Concurrent : Timer](
    implicit context: PulsarContext,
    cs: ContextShift[IO]
  ) = source[Throwable](responseTopic).client[F]
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
    responseTopic: Option[String] = None,
    batchingMaxPublishDelay: Duration = 1.millis
  )(
    //implicit monitoringPublisher: MonitoringPublisher = noPublisher
    implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
    responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
  ): PulsarRequestResponseServiceConfiguration[REQ, RESP] =
    new PulsarRequestResponseServiceConfiguration[REQ, RESP](
      requestTopic,
      requestTopic,
      responseTopic,
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