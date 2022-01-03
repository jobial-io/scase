package io.jobial.scase.aws.sqs

import cats.implicits._
import cats.effect.{Concurrent, ContextShift, IO, Timer}
import io.jobial.scase.aws.client.AwsContext
import io.jobial.scase.aws.client.Hash.uuid
import io.jobial.scase.core.impl.{ConsumerProducerRequestResponseClient, ConsumerProducerRequestResponseService}
import io.jobial.scase.core.{RequestHandler, RequestResponseClient, RequestResponseServiceConfiguration}
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.Future.successful

case class SqsRequestResponseServiceConfiguration[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
  serviceName: String,
  requestQueueName: String,
  cleanup: Boolean
)(
  //implicit monitoringPublisher: MonitoringPublisher = noPublisher
  implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
  responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
) extends RequestResponseServiceConfiguration[REQ, RESP] {

  //  def createQueues = 
  //    SqsRequestResponseQueues[REQ, RESP](queueNamePrefix, cleanup)

  //lazy val queues = createQueues

  val responseConsumerId = uuid(8)

  val requestQueueUrl = requestQueueName

  val responseQueueUrl = s"$requestQueueName-response-$responseConsumerId"

  def service[F[_] : Concurrent](requestProcessor: RequestHandler[F, REQ, RESP])(
    implicit awsContext: AwsContext = AwsContext(),
    cs: ContextShift[IO]
  ) = {
    val requestConsumer = SqsConsumer[F, REQ](requestQueueUrl, cleanup = false)
    val responseProducer = SqsProducer[F, Either[Throwable, RESP]](responseQueueUrl, cleanup = true)
    val service = ConsumerProducerRequestResponseService[F, REQ, RESP](
      requestConsumer,
      // add caching here
      responseQueueUrl => Concurrent[F].delay(responseProducer),
      requestProcessor
    )
    Concurrent[F].pure(service)
  }

  //https://cb372.github.io/scalacache/
  def client[F[_] : Concurrent : Timer](
    implicit awsContext: AwsContext = AwsContext(),
    cs: ContextShift[IO]
  ): F[RequestResponseClient[F, REQ, RESP]] = {
    val producer = SqsProducer[F, REQ](requestQueueUrl)
    val consumer = SqsConsumer[F, Either[Throwable, RESP]](responseQueueUrl)
    for {
      client <- ConsumerProducerRequestResponseClient[F, REQ, RESP](
        consumer,
        () => producer,
        responseQueueUrl
      )
    } yield client
  }


}

object SqsRequestResponseServiceConfiguration {

  def apply[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
    requestQueueName: String,
    cleanup: Boolean = false
  )(
    //implicit monitoringPublisher: MonitoringPublisher = noPublisher
    implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
    responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
  ): SqsRequestResponseServiceConfiguration[REQ, RESP] =
    SqsRequestResponseServiceConfiguration[REQ, RESP](requestQueueName, requestQueueName, cleanup)

}