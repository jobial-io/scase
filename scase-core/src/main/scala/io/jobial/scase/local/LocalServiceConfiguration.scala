package io.jobial.scase.local

import cats.effect.{Concurrent, Timer}
import cats.implicits._
import io.jobial.scase.core.impl.{ConsumerProducerRequestResponseClient, ConsumerProducerRequestResponseService}
import io.jobial.scase.core.{MessageProducer, RequestHandler, ServiceConfiguration}
import io.jobial.scase.inmemory.InMemoryConsumerProducer
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.serialization._


/**
 * Request-response client and service impl that internally wraps an existing request processor in a consumer-producer service
 * and uses in-memory queues to send requests and responses.
 */
case class LocalServiceConfiguration[REQ, RESP](
  serviceName: String
)(
  //implicit monitoringPublisher: MonitoringPublisher = noPublisher
) extends ServiceConfiguration {

  def serviceAndClient[F[_] : Concurrent : Timer](requestHandler: RequestHandler[F, REQ, RESP]) =
    for {
      requestQueue <- InMemoryConsumerProducer[F, REQ]
      responseQueue <- InMemoryConsumerProducer[F, Either[Throwable, RESP]]
      service <- ConsumerProducerRequestResponseService[F, REQ, RESP](
        requestQueue, { _ => Concurrent[F].delay(responseQueue) }: Option[String] => F[MessageProducer[F, Either[Throwable, RESP]]],
        requestHandler
      )
      client <- ConsumerProducerRequestResponseClient[F, REQ, RESP](
        responseQueue,
        () => requestQueue,
        Some("")
      )
    } yield (service, client)

}
