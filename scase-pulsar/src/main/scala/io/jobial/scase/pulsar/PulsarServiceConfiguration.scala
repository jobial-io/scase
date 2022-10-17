package io.jobial.scase.pulsar

import cats.effect.Concurrent
import cats.effect.Timer
import cats.implicits._
import io.jobial.scase.core.MessageHandler
import io.jobial.scase.core.MessageProducer
import io.jobial.scase.core.ReceiverClient
import io.jobial.scase.core.RequestHandler
import io.jobial.scase.core.RequestResponseClient
import io.jobial.scase.core.SenderClient
import io.jobial.scase.core.ServiceConfiguration
import io.jobial.scase.core.impl.CatsUtils
import io.jobial.scase.core.impl.ConsumerMessageHandlerService
import io.jobial.scase.core.impl.ConsumerProducerRequestResponseClient
import io.jobial.scase.core.impl.ConsumerProducerRequestResponseService
import io.jobial.scase.core.impl.ConsumerProducerStreamService
import io.jobial.scase.core.impl.ConsumerReceiverClient
import io.jobial.scase.core.impl.ProducerSenderClient
import io.jobial.scase.core.impl.ResponseProducerIdNotFound
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshaller
import io.jobial.scase.marshalling.Unmarshaller
import io.jobial.scase.pulsar.PulsarServiceConfiguration.destination
import io.jobial.scase.pulsar.PulsarServiceConfiguration.requestResponse
import io.jobial.scase.pulsar.PulsarServiceConfiguration.source
import org.apache.pulsar.client.api.SubscriptionInitialPosition
import org.apache.pulsar.client.api.SubscriptionInitialPosition.Earliest
import java.time.Instant
import java.util.UUID.randomUUID
import scala.concurrent.duration._

class PulsarMessageHandlerServiceConfiguration[M: Marshaller : Unmarshaller](
  val serviceName: String,
  val requestTopic: String,
  val patternAutoDiscoveryPeriod: Option[FiniteDuration],
  val subscriptionInitialPosition: Option[SubscriptionInitialPosition],
  val subscriptionInitialPublishTime: Option[Instant],
  val subscriptionName: String
) extends ServiceConfiguration {

  def service[F[_] : Concurrent : Timer](messageHandler: MessageHandler[F, M])(
    implicit context: PulsarContext
  ) =
    for {
      consumer <- PulsarConsumer[F, M](
        requestTopic,
        patternAutoDiscoveryPeriod,
        subscriptionInitialPosition,
        subscriptionInitialPublishTime,
        subscriptionName
      )
      service = new ConsumerMessageHandlerService(
        consumer,
        messageHandler
      )
    } yield service

  def client[F[_] : Concurrent : Timer](
    implicit context: PulsarContext
  ) = destination[M](requestTopic).client[F]

}

class PulsarRequestResponseServiceConfiguration[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
  val serviceName: String,
  val requestTopic: String,
  val responseTopicOverride: Option[String],
  val batchingMaxPublishDelay: Option[FiniteDuration],
  val patternAutoDiscoveryPeriod: Option[FiniteDuration],
  val subscriptionInitialPosition: Option[SubscriptionInitialPosition],
  val subscriptionInitialPublishTime: Option[Instant]
)(
  //implicit monitoringPublisher: MonitoringPublisher = noPublisher
  implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
  responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
) extends ServiceConfiguration with CatsUtils with Logging {

  def service[F[_] : Concurrent : Timer](requestHandler: RequestHandler[F, REQ, RESP])(
    implicit context: PulsarContext
  ) =
    for {
      consumer <- PulsarConsumer[F, REQ](requestTopic)
      service <- ConsumerProducerRequestResponseService[F, REQ, RESP](
        consumer, { responseTopicFromMessage =>
          responseTopicOverride.orElse(responseTopicFromMessage) match {
            case Some(responseTopic) =>
              PulsarProducer[F, Either[Throwable, RESP]](responseTopic, batchingMaxPublishDelay).map(p => p: MessageProducer[F, Either[Throwable, RESP]])
            case None =>
              raiseError(ResponseProducerIdNotFound("Not found response producer id in request"))
          }
        }: Option[String] => F[MessageProducer[F, Either[Throwable, RESP]]],
        requestHandler
      )
    } yield service

  def client[F[_] : Concurrent : Timer](
    implicit context: PulsarContext
  ): F[RequestResponseClient[F, REQ, RESP]] = {
    for {
      producer <- PulsarProducer[F, REQ](requestTopic, batchingMaxPublishDelay)
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
  val batchingMaxPublishDelay: Option[FiniteDuration],
  val patternAutoDiscoveryPeriod: Option[FiniteDuration],
  val subscriptionInitialPosition: Option[SubscriptionInitialPosition],
  val subscriptionInitialPublishTime: Option[Instant]
)(
  //implicit monitoringPublisher: MonitoringPublisher = noPublisher
  implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
  responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
) extends ServiceConfiguration {

  def service[F[_] : Concurrent : Timer](requestHandler: RequestHandler[F, REQ, RESP])(
    implicit context: PulsarContext
  ) = requestResponse[REQ, RESP](
    requestTopic,
    Some(responseTopic),
    batchingMaxPublishDelay,
    patternAutoDiscoveryPeriod,
    subscriptionInitialPosition,
    subscriptionInitialPublishTime
  ).service(requestHandler)

  def senderClient[F[_] : Concurrent : Timer](
    implicit context: PulsarContext
  ) = destination[REQ](requestTopic, batchingMaxPublishDelay).client[F]

  def receiverClient[F[_] : Concurrent : Timer](
    implicit context: PulsarContext
  ) = source[Either[Throwable, RESP]](
    responseTopic,
    patternAutoDiscoveryPeriod,
    subscriptionInitialPosition,
    subscriptionInitialPublishTime
  ).client[F]

}

class PulsarStreamServiceWithErrorTopicConfiguration[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
  val serviceName: String,
  val requestTopic: String,
  val responseTopic: String,
  val errorTopic: String,
  val batchingMaxPublishDelay: Option[FiniteDuration]
)(
  //implicit monitoringPublisher: MonitoringPublisher = noPublisher
  implicit errorMarshaller: Marshaller[Throwable],
  errorUnmarshaller: Unmarshaller[Throwable]
) extends ServiceConfiguration {

  def service[F[_] : Concurrent : Timer](requestHandler: RequestHandler[F, REQ, RESP])(
    implicit context: PulsarContext
  ) =
    for {
      consumer <- PulsarConsumer[F, REQ](requestTopic)
      service <- ConsumerProducerStreamService[F, REQ, RESP](
        consumer, { _ =>
          for {
            producer <- PulsarProducer[F, RESP](responseTopic, batchingMaxPublishDelay)
          } yield producer
        }: Option[String] => F[MessageProducer[F, RESP]], { _ =>
          for {
            producer <- PulsarProducer[F, Throwable](responseTopic, batchingMaxPublishDelay)
          } yield producer
        }: Option[String] => F[MessageProducer[F, Throwable]],
        requestHandler,
        defaultProducerId = None,
        autoCommitRequest = false,
        autoCommitFailedRequest = false
      )
    } yield service

  def senderClient[F[_] : Concurrent : Timer](
    implicit context: PulsarContext
  ) = destination[REQ](requestTopic).client[F]

  def responseReceiverClient[F[_] : Concurrent : Timer](
    implicit context: PulsarContext
  ) = source[RESP](responseTopic).client[F]

  def errorReceiverClient[F[_] : Concurrent : Timer](
    implicit context: PulsarContext
  ) = source[Throwable](errorTopic).client[F]
}

class PulsarMessageSourceServiceConfiguration[M: Unmarshaller](
  val sourceTopic: String,
  val patternAutoDiscoveryPeriod: Option[FiniteDuration] = Some(1.second),
  val subscriptionInitialPosition: Option[SubscriptionInitialPosition] = Some(Earliest),
  val subscriptionInitialPublishTime: Option[Instant] = None
) {
  def client[F[_] : Concurrent : Timer](
    implicit context: PulsarContext
  ): F[ReceiverClient[F, M]] =
    for {
      consumer <- PulsarConsumer[F, M](
        sourceTopic,
        patternAutoDiscoveryPeriod,
        subscriptionInitialPosition,
        subscriptionInitialPublishTime
      )
      client <- ConsumerReceiverClient[F, M](consumer)
    } yield client
}

class PulsarMessageDestinationServiceConfiguration[M: Marshaller](
  val topic: String,
  val batchingMaxPublishDelay: Option[FiniteDuration]
) {

  def client[F[_] : Concurrent : Timer](
    implicit context: PulsarContext
  ): F[SenderClient[F, M]] =
    for {
      producer <- PulsarProducer[F, M](topic, batchingMaxPublishDelay)
      client <- ProducerSenderClient[F, M](
        producer
      )
    } yield client
}

object PulsarServiceConfiguration {

  def requestResponse[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
    requestTopic: String,
    responseTopicOverride: Option[String] = None,
    batchingMaxPublishDelay: Option[FiniteDuration] = Some(1.millis),
    patternAutoDiscoveryPeriod: Option[FiniteDuration] = Some(1.second),
    subscriptionInitialPosition: Option[SubscriptionInitialPosition] = Some(Earliest),
    subscriptionInitialPublishTime: Option[Instant] = None
  )(
    //implicit monitoringPublisher: MonitoringPublisher = noPublisher
    implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
    responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
  ): PulsarRequestResponseServiceConfiguration[REQ, RESP] =
    new PulsarRequestResponseServiceConfiguration[REQ, RESP](
      requestTopic,
      requestTopic,
      responseTopicOverride,
      batchingMaxPublishDelay,
      patternAutoDiscoveryPeriod,
      subscriptionInitialPosition,
      subscriptionInitialPublishTime
    )

  def stream[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
    requestTopic: String,
    responseTopic: String,
    batchingMaxPublishDelay: Option[FiniteDuration],
    patternAutoDiscoveryPeriod: Option[FiniteDuration],
    subscriptionInitialPosition: Option[SubscriptionInitialPosition],
    subscriptionInitialPublishTime: Option[Instant]
  )(
    //implicit monitoringPublisher: MonitoringPublisher = noPublisher
    implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
    responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
  ): PulsarStreamServiceConfiguration[REQ, RESP] =
    new PulsarStreamServiceConfiguration[REQ, RESP](
      requestTopic,
      requestTopic,
      responseTopic,
      batchingMaxPublishDelay,
      patternAutoDiscoveryPeriod,
      subscriptionInitialPosition,
      subscriptionInitialPublishTime
    )

  def stream[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
    requestTopic: String,
    responseTopic: String
  )(
    //implicit monitoringPublisher: MonitoringPublisher = noPublisher
    implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
    responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
  ): PulsarStreamServiceConfiguration[REQ, RESP] =
    stream[REQ, RESP](
      requestTopic,
      responseTopic,
      Some(1.millis),
      Some(1.second),
      Some(Earliest),
      None
    )

  def stream[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
    requestTopic: String,
    responseTopic: String,
    errorTopic: String,
    batchingMaxPublishDelay: Option[FiniteDuration] = Some(1.millis),
    patternAutoDiscoveryPeriod: Option[FiniteDuration] = Some(1.second),
    subscriptionInitialPosition: Option[SubscriptionInitialPosition] = Some(Earliest),
    subscriptionInitialPublishTime: Option[Instant] = None
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

  def handler[M: Marshaller : Unmarshaller](
    requestTopic: String,
    patternAutoDiscoveryPeriod: Option[FiniteDuration] = Some(1.second),
    subscriptionInitialPosition: Option[SubscriptionInitialPosition] = Some(Earliest),
    subscriptionInitialPublishTime: Option[Instant] = None,
    subscriptionName: String = s"subscription-${randomUUID}"
  ) =
    new PulsarMessageHandlerServiceConfiguration[M](requestTopic,
      requestTopic,
      patternAutoDiscoveryPeriod,
      subscriptionInitialPosition,
      subscriptionInitialPublishTime,
      subscriptionName
    )

  def source[M: Unmarshaller](
    sourceTopic: String,
    patternAutoDiscoveryPeriod: Option[FiniteDuration] = Some(1.second),
    subscriptionInitialPosition: Option[SubscriptionInitialPosition] = Some(Earliest),
    subscriptionInitialPublishTime: Option[Instant] = None
  ) = new PulsarMessageSourceServiceConfiguration(
    sourceTopic,
    patternAutoDiscoveryPeriod,
    subscriptionInitialPosition,
    subscriptionInitialPublishTime
  )

  def destination[M: Marshaller](
    topic: String,
    batchingMaxPublishDelay: Option[FiniteDuration] = Some(1.millis)
  ) = new PulsarMessageDestinationServiceConfiguration[M](topic, batchingMaxPublishDelay)

}