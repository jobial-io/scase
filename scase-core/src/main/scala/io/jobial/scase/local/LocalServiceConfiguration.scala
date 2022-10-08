package io.jobial.scase.local

import cats.effect.Concurrent
import cats.effect.Timer
import cats.implicits._
import io.jobial.scase.core.MessageProducer
import io.jobial.scase.core.RequestHandler
import io.jobial.scase.core.ServiceConfiguration
import io.jobial.scase.core.impl.CatsUtils
import io.jobial.scase.core.impl.ConsumerProducerRequestResponseClient
import io.jobial.scase.core.impl.ConsumerProducerRequestResponseService
import io.jobial.scase.inmemory.InMemoryConsumer
import io.jobial.scase.inmemory.InMemoryProducer
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.serialization._


/**
 * Request-response client and service impl that internally wraps an existing request processor in a consumer-producer service
 * and uses in-memory queues to send requests and responses.
 */
class LocalServiceConfiguration[REQ, RESP](
  val serviceName: String
) extends ServiceConfiguration with CatsUtils with Logging {

  def service[F[_] : Concurrent : Timer](requestHandler: RequestHandler[F, REQ, RESP]) =
    for {
      requestQueue <- InMemoryConsumer[F, REQ]
      responseQueue <- InMemoryProducer[F, Either[Throwable, RESP]]
      service <- ConsumerProducerRequestResponseService[F, REQ, RESP](
        // TODO: could create a number of producers here and assign them to clients randomly as an optimisation 
        requestQueue, { _ => delay(responseQueue) }: Option[String] => F[MessageProducer[F, Either[Throwable, RESP]]],
        requestHandler
      )
    } yield service

  def client[F[_] : Concurrent : Timer](service: ConsumerProducerRequestResponseService[F, REQ, RESP]) =
    for {
      responseProducer <- service.responseProducer(None)
      responseConsumer <- responseProducer.asInstanceOf[InMemoryProducer[F, Either[Throwable, RESP]]].consumer
      requestProducer <- service.requestConsumer.asInstanceOf[InMemoryConsumer[F, REQ]].producer
      client <- ConsumerProducerRequestResponseClient[F, REQ, RESP](
        responseConsumer,
        () => requestProducer,
        None
      )
    } yield client

  def serviceAndClient[F[_] : Concurrent : Timer](requestHandler: RequestHandler[F, REQ, RESP]) =
    for {
      service <- service(requestHandler)
      client <- client(service)
    } yield (service, client)

}

object LocalServiceConfiguration {

  def apply[REQ, RESP](
    serviceName: String
  ) = new LocalServiceConfiguration[REQ, RESP](serviceName)
}