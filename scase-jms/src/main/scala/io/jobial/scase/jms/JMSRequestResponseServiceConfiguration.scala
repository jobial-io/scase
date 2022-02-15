package io.jobial.scase.jms

import cats.effect.{Concurrent, ContextShift, IO, Timer}
import cats.implicits._
import io.jobial.scase.core.impl.{ConsumerProducerRequestResponseClient, ConsumerProducerRequestResponseService}
import io.jobial.scase.core.{MessageProducer, RequestHandler, RequestResponseClient, ServiceConfiguration}
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}
import io.jobial.scase.jms.JMSProducer

import javax.jms.{Destination, JMSContext, Session}
import scala.concurrent.duration.{Duration, DurationInt}


class JMSRequestResponseServiceConfiguration[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
  val serviceName: String,
  requestDestination: Destination,
  responseDestination: Destination,
  requestTimeout: Duration
)(
  //implicit monitoringPublisher: MonitoringPublisher = noPublisher
  implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
  responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
) extends ServiceConfiguration {

  def service[F[_] : Concurrent](requestHandler: RequestHandler[F, REQ, RESP])(
    implicit session: Session,
    cs: ContextShift[IO]
  ) =
    for {
      consumer <- JMSConsumer[F, REQ](requestDestination)
      service <- ConsumerProducerRequestResponseService[F, REQ, RESP](
        consumer,
        { responseTopic => Concurrent[F].delay(JMSProducer[F, Either[Throwable, RESP]](responseDestination)) }: String => F[MessageProducer[F, Either[Throwable, RESP]]],
        requestHandler,
        defaultProducerId = Some("")
      )
    } yield service

  def client[F[_] : Concurrent : Timer](
    implicit session: Session,
    cs: ContextShift[IO]
  ): F[RequestResponseClient[F, REQ, RESP]] = {
    val producer = JMSProducer[F, REQ](requestDestination)
    for {
      consumer <- JMSConsumer[F, Either[Throwable, RESP]](responseDestination)
      client <- ConsumerProducerRequestResponseClient[F, REQ, RESP](
        consumer,
        () => producer,
        None
      )
    } yield client
  }

}

object JMSRequestResponseServiceConfiguration {

  def apply[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
    serviceName: String,
    requestDestination: Destination,
    responseDestination: Destination,
    requestTimeout: Duration = 5.minutes
  )(
    //implicit monitoringPublisher: MonitoringPublisher = noPublisher
    implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
    responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
  ): JMSRequestResponseServiceConfiguration[REQ, RESP] =
    new JMSRequestResponseServiceConfiguration[REQ, RESP](
      serviceName,
      requestDestination,
      responseDestination,
      requestTimeout
    )
}