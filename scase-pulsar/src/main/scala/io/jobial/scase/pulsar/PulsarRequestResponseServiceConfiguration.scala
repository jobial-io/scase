package io.jobial.scase.pulsar

import cats.effect.{Concurrent, ContextShift, IO, Timer}
import io.jobial.scase.core.{MessageConsumer, MessageProducer, RequestHandler, RequestResponseClient, ServiceConfiguration}
import cats.implicits._
import io.jobial.scase.core.impl.{ConsumerProducerRequestResponseClient, ConsumerProducerRequestResponseService, ResponseProducerIdNotFound}
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}

import java.util.UUID.randomUUID
import scala.concurrent.duration._

case class PulsarRequestResponseServiceConfiguration[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
  serviceName: String,
  requestTopic: String,
  responseTopicOverride: Option[String],
  batchingMaxPublishDelay: Duration
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
              Concurrent[F].delay(PulsarProducer[F, Either[Throwable, RESP]](responseTopic))
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
    val producer = PulsarProducer[F, REQ](requestTopic)
    for {
      consumer <- PulsarConsumer[F, Either[Throwable, RESP]](responseTopic)
      client <- ConsumerProducerRequestResponseClient[F, REQ, RESP](
        consumer,
        () => producer,
        Some(responseTopic)
      )
    } yield client
  }

}

object PulsarRequestResponseServiceConfiguration {

  def apply[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
    requestTopic: String,
    responseTopicOverride: Option[String] = None,
    batchingMaxPublishDelay: Duration = 1.millis
  )(
    //implicit monitoringPublisher: MonitoringPublisher = noPublisher
    implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
    responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
  ): PulsarRequestResponseServiceConfiguration[REQ, RESP] =
    PulsarRequestResponseServiceConfiguration[REQ, RESP](
      requestTopic,
      requestTopic,
      responseTopicOverride,
      batchingMaxPublishDelay
    )
}