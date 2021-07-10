package io.jobial.scase.local

import cats.effect.Concurrent
import cats.implicits._
import io.jobial.scase.core.{ConsumerProducerRequestResponseClient, ConsumerProducerRequestResponseService, RequestProcessor, RequestResponseService, RequestResponseServiceConfiguration}
import io.jobial.scase.inmemory.InMemoryQueue
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.serialization._


/**
 * Request-response client and service impl that internally wraps an existing request processor in a consumer-producer service
 * and uses in-memory queues to send requests and responses.
 */
case class LocalRequestResponseServiceConfiguration[REQ, RESP](
  serviceName: String
)(
  //implicit monitoringPublisher: MonitoringPublisher = noPublisher
  
) extends RequestResponseServiceConfiguration[REQ, RESP] {

  //  lazy val requestQueue = InMemoryQueue[F, REQ]()
  //
  //  lazy val responseQueue = InMemoryQueue[F, Either[Throwable, RESP]]()

  def serviceAndClient[F[_]](requestProcessor: RequestProcessor[F, REQ, RESP])(implicit s: Concurrent[F]) =
    for {
      requestQueue <- InMemoryQueue[F, REQ]
      responseQueue <- InMemoryQueue[F, Either[Throwable, RESP]]
      service = ConsumerProducerRequestResponseService[F, REQ, RESP](
        requestQueue,
        { _ => Concurrent[F].delay(responseQueue) },
        requestProcessor
      )
      client <- ConsumerProducerRequestResponseClient[F, REQ, RESP](
        responseQueue,
        () => requestQueue,
        ""
      )
    } yield (service, client)

  //  lazy val client =
  //    ConsumerProducerRequestResponseClient[F, REQ, RESP](responseQueue, () => requestQueue,
  //      responseConsumerId = randomUUID().toString, name = serviceName)
}

//case class LocalRequestResponseService[F[_], REQ, RESP](
//  requestProcessor: RequestProcessor[F, REQ, RESP],
//  requestQueue: InMemoryQueue[F, REQ],
//  responseQueue: InMemoryQueue[F, Either[Throwable, RESP]]
//)(
//  implicit s: Concurrent[F]
//) extends RequestResponseService[F, REQ, RESP] with Logging {
//
//  val service = ConsumerProducerRequestResponseService[F, REQ, RESP](
//    requestQueue,
//    { _ => Concurrent[F].delay(responseQueue) },
//    requestProcessor
//  )
//}