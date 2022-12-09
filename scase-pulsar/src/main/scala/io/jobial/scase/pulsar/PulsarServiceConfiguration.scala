package io.jobial.scase.pulsar

import cats.implicits._
import io.jobial.scase.core.MessageHandler
import io.jobial.scase.core.MessageProducer
import io.jobial.scase.core.ReceiverClient
import io.jobial.scase.core.RequestHandler
import io.jobial.scase.core.RequestResponseClient
import io.jobial.scase.core.SenderClient
import io.jobial.scase.core.ServiceConfiguration
import io.jobial.scase.core.impl.AsyncEffect
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
import org.apache.pulsar.client.api.SubscriptionInitialPosition.Latest
import java.time.Instant
import java.util.UUID.randomUUID
import scala.concurrent.duration._
import scala.util.matching.Regex

class PulsarMessageHandlerServiceConfiguration[M: Marshaller : Unmarshaller](
  val serviceName: String,
  val requestTopic: Either[String, Regex],
  val patternAutoDiscoveryPeriod: Option[FiniteDuration],
  val subscriptionInitialPosition: Option[SubscriptionInitialPosition],
  val subscriptionInitialPublishTime: Option[Instant],
  val subscriptionName: String
) extends ServiceConfiguration with CatsUtils with Logging {

  def service[F[_] : AsyncEffect](messageHandler: MessageHandler[F, M])(
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

  def client[F[_] : AsyncEffect](
    implicit context: PulsarContext
  ) = requestTopic match {
    case Left(requestTopic) =>
      destination[M](requestTopic).client[F]
    case Right(_) =>
      raiseError(new IllegalStateException("topic pattern is not supported"))
  }
}

class PulsarRequestResponseServiceConfiguration[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
  val serviceName: String,
  val requestTopic: Either[String, Regex],
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

  def service[F[_] : AsyncEffect](requestHandler: RequestHandler[F, REQ, RESP])(
    implicit context: PulsarContext
  ) =
    for {
      consumer <- PulsarConsumer[F, REQ](
        requestTopic,
        patternAutoDiscoveryPeriod,
        subscriptionInitialPosition,
        subscriptionInitialPublishTime
      )
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

  def client[F[_] : AsyncEffect](
    implicit context: PulsarContext
  ): F[RequestResponseClient[F, REQ, RESP]] =
    for {
      producer <- PulsarProducer[F, REQ](requestTopic.left.get, batchingMaxPublishDelay)
      responseTopic = responseTopicOverride.getOrElse(s"${requestTopic.left.get}-response-${randomUUID}")
      consumer <- PulsarConsumer[F, Either[Throwable, RESP]](Left(responseTopic))
      client <- ConsumerProducerRequestResponseClient[F, REQ, RESP](
        consumer,
        () => producer,
        Some(responseTopic)
      )
    } yield client
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

  def service[F[_] : AsyncEffect](requestHandler: RequestHandler[F, REQ, RESP])(
    implicit context: PulsarContext
  ) = requestResponse[REQ, RESP](
    requestTopic,
    Some(responseTopic),
    batchingMaxPublishDelay,
    patternAutoDiscoveryPeriod,
    subscriptionInitialPosition,
    subscriptionInitialPublishTime
  ).service(requestHandler)

  def senderClient[F[_] : AsyncEffect](
    implicit context: PulsarContext
  ) = destination[REQ](requestTopic, batchingMaxPublishDelay).client[F]

  def receiverClient[F[_] : AsyncEffect](
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

  def service[F[_] : AsyncEffect](requestHandler: RequestHandler[F, REQ, RESP])(
    implicit context: PulsarContext
  ) =
    for {
      consumer <- PulsarConsumer[F, REQ](Left(requestTopic))
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

  def senderClient[F[_] : AsyncEffect](
    implicit context: PulsarContext
  ) = destination[REQ](requestTopic).client[F]

  def responseReceiverClient[F[_] : AsyncEffect](
    implicit context: PulsarContext
  ) = source[RESP](responseTopic).client[F]

  def errorReceiverClient[F[_] : AsyncEffect](
    implicit context: PulsarContext
  ) = source[Throwable](errorTopic).client[F]
}

class PulsarMessageSourceServiceConfiguration[M: Unmarshaller](
  val sourceTopic: Either[String, Regex],
  val patternAutoDiscoveryPeriod: Option[FiniteDuration] = Some(1.second),
  val subscriptionInitialPosition: Option[SubscriptionInitialPosition] = Some(Latest),
  val subscriptionInitialPublishTime: Option[Instant] = None
) {
  def client[F[_] : AsyncEffect](
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

  def client[F[_] : AsyncEffect](
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
    subscriptionInitialPosition: Option[SubscriptionInitialPosition] = Some(Latest),
    subscriptionInitialPublishTime: Option[Instant] = None
  )(
    //implicit monitoringPublisher: MonitoringPublisher = noPublisher
    implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
    responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
  ): PulsarRequestResponseServiceConfiguration[REQ, RESP] =
    new PulsarRequestResponseServiceConfiguration[REQ, RESP](
      requestTopic,
      Left(requestTopic),
      responseTopicOverride,
      batchingMaxPublishDelay,
      patternAutoDiscoveryPeriod,
      subscriptionInitialPosition,
      subscriptionInitialPublishTime
    )

  def requestResponse[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
    requestTopic: Either[String, Regex]
  )(
    //implicit monitoringPublisher: MonitoringPublisher = noPublisher
    implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
    responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
  ): PulsarRequestResponseServiceConfiguration[REQ, RESP] =
    new PulsarRequestResponseServiceConfiguration[REQ, RESP](
      requestTopic match {
        case Right(r) =>
          r.toString
        case Left(l) =>
          l.toString
      },
      requestTopic,
      None,
      Some(1.millis),
      Some(1.second),
      Some(Latest),
      None
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
      Some(Latest),
      None
    )

  def stream[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
    requestTopic: String,
    responseTopic: String,
    errorTopic: String,
    batchingMaxPublishDelay: Option[FiniteDuration] = Some(1.millis),
    patternAutoDiscoveryPeriod: Option[FiniteDuration] = Some(1.second),
    subscriptionInitialPosition: Option[SubscriptionInitialPosition] = Some(Latest),
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
    subscriptionInitialPosition: Option[SubscriptionInitialPosition] = Some(Latest),
    subscriptionInitialPublishTime: Option[Instant] = None,
    subscriptionName: String = s"subscription-${randomUUID}"
  ) =
    new PulsarMessageHandlerServiceConfiguration[M](
      requestTopic,
      Left(requestTopic),
      patternAutoDiscoveryPeriod,
      subscriptionInitialPosition,
      subscriptionInitialPublishTime,
      subscriptionName
    )

  def handler[M: Marshaller : Unmarshaller](
    requestTopic: Regex,
    patternAutoDiscoveryPeriod: Option[FiniteDuration],
    subscriptionInitialPosition: Option[SubscriptionInitialPosition],
    subscriptionInitialPublishTime: Option[Instant],
    subscriptionName: String
  ) =
    new PulsarMessageHandlerServiceConfiguration[M](
      requestTopic.toString,
      Right(requestTopic),
      patternAutoDiscoveryPeriod,
      subscriptionInitialPosition,
      subscriptionInitialPublishTime,
      subscriptionName
    )

  def handler[M: Marshaller : Unmarshaller](
    requestTopic: Regex,
    subscriptionInitialPosition: Option[SubscriptionInitialPosition],
    subscriptionInitialPublishTime: Option[Instant],
    subscriptionName: String
  ) =
    new PulsarMessageHandlerServiceConfiguration[M](
      requestTopic.toString,
      Right(requestTopic),
      Some(1.second),
      subscriptionInitialPosition,
      subscriptionInitialPublishTime,
      subscriptionName
    )

  def handler[M: Marshaller : Unmarshaller](
    requestTopic: Regex,
    subscriptionInitialPosition: Option[SubscriptionInitialPosition]
  ) =
    new PulsarMessageHandlerServiceConfiguration[M](
      requestTopic.toString,
      Right(requestTopic),
      Some(1.second),
      subscriptionInitialPosition,
      None,
      s"subscription-${randomUUID}"
    )

  def handler[M: Marshaller : Unmarshaller](
    requestTopic: Regex
  ) =
    new PulsarMessageHandlerServiceConfiguration[M](
      requestTopic.toString,
      Right(requestTopic),
      Some(1.second),
      Some(Latest),
      None,
      s"subscription-${randomUUID}"
    )

  def source[M: Unmarshaller](
    sourceTopic: String,
    patternAutoDiscoveryPeriod: Option[FiniteDuration] = Some(1.second),
    subscriptionInitialPosition: Option[SubscriptionInitialPosition] = Some(Latest),
    subscriptionInitialPublishTime: Option[Instant] = None
  ) = new PulsarMessageSourceServiceConfiguration(
    Left(sourceTopic),
    patternAutoDiscoveryPeriod,
    subscriptionInitialPosition,
    subscriptionInitialPublishTime
  )

  def source[M: Unmarshaller](
    sourceTopic: Either[String, Regex]
  ) = new PulsarMessageSourceServiceConfiguration(
    sourceTopic,
    Some(1.second),
    Some(Latest),
    None
  )

  def destination[M: Marshaller](
    topic: String,
    batchingMaxPublishDelay: Option[FiniteDuration] = Some(1.millis)
  ) = new PulsarMessageDestinationServiceConfiguration[M](topic, batchingMaxPublishDelay)

}