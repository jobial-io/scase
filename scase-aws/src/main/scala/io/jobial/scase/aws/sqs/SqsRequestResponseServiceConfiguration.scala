package io.jobial.scase.aws.sqs

import cats.effect.{Concurrent, ContextShift, IO, Timer}
import cats.implicits._
import io.jobial.scase.aws.client.AwsContext
import io.jobial.scase.aws.client.Hash.uuid
import io.jobial.scase.core.impl.{ConsumerProducerRequestResponseClient, ConsumerProducerRequestResponseService}
import io.jobial.scase.core.{MessageProducer, RequestHandler, RequestResponseClient, ServiceConfiguration}
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}

case class SqsRequestResponseServiceConfiguration[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
  serviceName: String,
  requestQueueName: String,
  cleanup: Boolean
)(
  //implicit monitoringPublisher: MonitoringPublisher = noPublisher
  implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
  responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
) extends ServiceConfiguration {

  //  def createQueues = 
  //    SqsRequestResponseQueues[REQ, RESP](queueNamePrefix, cleanup)

  //lazy val queues = createQueues

  val responseProducerId = uuid(8)

  val requestQueueUrl = requestQueueName

  val responseQueueUrl = s"$requestQueueName-response-$responseProducerId"

  def service[F[_] : Concurrent](requestHandler: RequestHandler[F, REQ, RESP])(
    implicit awsContext: AwsContext = AwsContext(),
    cs: ContextShift[IO]
  ) = {
    val requestConsumer = SqsConsumer[F, REQ](requestQueueUrl, cleanup = false)
    val responseProducer = SqsProducer[F, Either[Throwable, RESP]](responseQueueUrl, cleanup = true)
    for {
      service <- ConsumerProducerRequestResponseService[F, REQ, RESP](
        requestConsumer,
        { _ => Concurrent[F].delay(responseProducer) }: String => F[MessageProducer[F, Either[Throwable, RESP]]],
        requestHandler
      )
    } yield service
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