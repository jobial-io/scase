package io.jobial.scase.pulsar.client

import cats.effect.{Concurrent, ContextShift, IO}
import cats.implicits._
import io.jobial.scase.core.{ConsumerProducerRequestResponseService, RequestProcessor, RequestResponseClient, RequestResponseServiceConfiguration}
import io.jobial.scase.marshalling.serialization._


/**
 * Request-response client and service impl that internally wraps an existing request processor in a consumer-producer service
 * and uses in-memory queues to send requests and responses.
 */
case class PulsarRequestResponseServiceConfiguration[REQ, RESP](
  serviceName: String,
  requestTopic: String
)(
  //implicit monitoringPublisher: MonitoringPublisher = noPublisher
  implicit context: PulsarContext,
  cs: ContextShift[IO]
) extends RequestResponseServiceConfiguration[REQ, RESP] {

  def service[F[_] : Concurrent](requestProcessor: RequestProcessor[F, REQ, RESP]) =
    for {
      consumer <- PulsarConsumer[F, REQ](requestTopic)
      service = ConsumerProducerRequestResponseService[F, REQ, RESP](
        consumer,
        { responseTopic => Concurrent[F].delay(PulsarProducer[F, Either[Throwable, RESP]](responseTopic)) },
        requestProcessor
      )
    } yield service

  def client[F[_] : Concurrent]: F[RequestResponseClient[F, REQ, RESP]] = ???

}
