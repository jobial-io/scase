package io.jobial.scase.jms

import cats.effect.{Concurrent, ContextShift, IO, Timer}
import cats.implicits._
import io.jobial.scase.core.impl.{ConsumerProducerRequestResponseClient, ConsumerProducerRequestResponseService}
import io.jobial.scase.core.{MessageProducer, RequestHandler, RequestResponseClient, ServiceConfiguration}
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}
import io.jobial.scase.jms.JMSProducer

import javax.jms.{Destination, JMSContext}
import scala.concurrent.duration.Duration


case class JMSRequestResponseServiceConfiguration[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
  serviceName: String,
  requestDestination: Destination,
  responseDestination: Destination,
  requestTimeout: Duration
)(
  //implicit monitoringPublisher: MonitoringPublisher = noPublisher
  implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
  responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
) extends ServiceConfiguration {

  def service[F[_] : Concurrent](requestHandler: RequestHandler[F, REQ, RESP])(
    implicit context: JMSContext,
    cs: ContextShift[IO]
  ) =
    for {
      consumer <- JMSConsumer[F, REQ](responseDestination)
      service <- ConsumerProducerRequestResponseService[F, REQ, RESP](
        consumer,
        { responseTopic => Concurrent[F].delay(JMSProducer[F, Either[Throwable, RESP]](responseDestination)) }: String => F[MessageProducer[F, Either[Throwable, RESP]]],
        requestHandler
      )
    } yield service

  def client[F[_] : Concurrent : Timer](
    implicit context: JMSContext,
    cs: ContextShift[IO]
  ): F[RequestResponseClient[F, REQ, RESP]] = {
    val producer = JMSProducer[F, REQ](requestDestination)
    for {
      consumer <- JMSConsumer[F, Either[Throwable, RESP]](responseDestination)
      client <- ConsumerProducerRequestResponseClient[F, REQ, RESP](
        consumer,
        () => producer,
        ""
      )
    } yield client
  }

}

object JMSRequestResponseServiceConfiguration {

  def apply[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
    serviceName: String,
    requestDestination: Destination,
    responseDestination: Destination,
    requestTimeout: Duration
  )(
    //implicit monitoringPublisher: MonitoringPublisher = noPublisher
    implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
    responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
  ): JMSRequestResponseServiceConfiguration[REQ, RESP] =
    JMSRequestResponseServiceConfiguration[REQ, RESP](
      serviceName,
      requestDestination,
      responseDestination,
      requestTimeout
    )
}