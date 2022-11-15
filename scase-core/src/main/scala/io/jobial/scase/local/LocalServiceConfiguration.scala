package io.jobial.scase.local

import cats.implicits._
import io.jobial.scase.core.MessageHandler
import io.jobial.scase.core.MessageProducer
import io.jobial.scase.core.RequestHandler
import io.jobial.scase.core.ServiceConfiguration
import io.jobial.scase.core.impl.CatsUtils
import io.jobial.scase.core.impl.ConcurrentEffect
import io.jobial.scase.core.impl.ConsumerMessageHandlerService
import io.jobial.scase.core.impl.ConsumerProducerRequestResponseClient
import io.jobial.scase.core.impl.ConsumerProducerRequestResponseService
import io.jobial.scase.core.impl.ProducerSenderClient
import io.jobial.scase.core.impl.TemporalEffect
import io.jobial.scase.inmemory.InMemoryConsumer
import io.jobial.scase.inmemory.InMemoryProducer
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.serialization._


/**
 * Request-response client and service impl that internally wraps an existing request processor in a consumer-producer service
 * and uses in-memory queues to send requests and responses.
 */
class LocalRequestResponseServiceConfiguration[REQ, RESP](
  val serviceName: String
) extends ServiceConfiguration with CatsUtils with Logging {

  def service[F[_] : TemporalEffect](requestHandler: RequestHandler[F, REQ, RESP]) =
    for {
      requestQueue <- InMemoryConsumer[F, REQ]
      responseQueue <- InMemoryProducer[F, Either[Throwable, RESP]]
      service <- ConsumerProducerRequestResponseService[F, REQ, RESP](
        // TODO: could create a number of producers here and assign them to clients randomly as an optimisation 
        requestQueue, { _ => delay(responseQueue) }: Option[String] => F[MessageProducer[F, Either[Throwable, RESP]]],
        requestHandler
      )
    } yield service

  def client[F[_] : TemporalEffect](service: ConsumerProducerRequestResponseService[F, REQ, RESP]) =
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

  def serviceAndClient[F[_] : TemporalEffect](requestHandler: RequestHandler[F, REQ, RESP]) =
    for {
      service <- service(requestHandler)
      client <- client(service)
    } yield (service, client)

}

class LocalMessageHandlerServiceConfiguration[REQ](
  val serviceName: String
) extends ServiceConfiguration with CatsUtils with Logging {

  def service[F[_] : TemporalEffect](messageHandler: MessageHandler[F, REQ]) =
    for {
      requestQueue <- InMemoryConsumer[F, REQ]
      service <- ConsumerMessageHandlerService[F, REQ](
        requestQueue,
        messageHandler
      )
    } yield service


  def client[F[_] : ConcurrentEffect, REQ](service: ConsumerMessageHandlerService[F, REQ]) =
    for {
      producer <- service.consumer.asInstanceOf[InMemoryConsumer[F, REQ]].producer
      client <- ProducerSenderClient[F, REQ](producer)
    } yield client
}

object LocalServiceConfiguration {

  def requestResponse[REQ, RESP](
    serviceName: String
  ) = new LocalRequestResponseServiceConfiguration[REQ, RESP](serviceName)

  def handler[REQ](
    serviceName: String
  ) = new LocalMessageHandlerServiceConfiguration[REQ](serviceName)
}