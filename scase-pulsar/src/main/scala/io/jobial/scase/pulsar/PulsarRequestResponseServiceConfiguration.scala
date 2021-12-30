package io.jobial.scase.pulsar

import cats.effect.{Concurrent, ContextShift, IO, Timer}
import io.jobial.scase.core.{ConsumerProducerRequestResponseClient, ConsumerProducerRequestResponseService, RequestProcessor, RequestResponseClient, RequestResponseServiceConfiguration}
import cats.implicits._
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}

import java.util.UUID.randomUUID
import scala.concurrent.duration._

case class PulsarRequestResponseServiceConfiguration[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
  serviceName: String,
  requestTopic: String,
  responseTopicOverride: Option[String] = None,
  requestTimeout: Duration = 5.minutes,
  batchingMaxPublishDelay: Duration = 1.millis,
)(
  //implicit monitoringPublisher: MonitoringPublisher = noPublisher
  implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
  responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
) extends RequestResponseServiceConfiguration[REQ, RESP] {

  val responseTopic = responseTopicOverride.getOrElse(s"$requestTopic-response-${randomUUID}")

  def service[F[_] : Concurrent](requestProcessor: RequestProcessor[F, REQ, RESP])(
    implicit context: PulsarContext,
    cs: ContextShift[IO]
  ) =
    for {
      consumer <- PulsarConsumer[F, REQ](requestTopic)
      service = ConsumerProducerRequestResponseService[F, REQ, RESP](
        consumer,
        { responseTopic => Concurrent[F].delay(PulsarProducer[F, Either[Throwable, RESP]](responseTopic)) },
        requestProcessor
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
        responseTopic
      )
    } yield client
  }

}

object PulsarRequestResponseServiceConfiguration {
  
}