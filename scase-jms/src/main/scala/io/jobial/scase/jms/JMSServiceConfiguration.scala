package io.jobial.scase.jms

import cats.effect.{Concurrent, Timer}
import cats.implicits._
import io.jobial.scase.core.impl.{ConsumerProducerRequestResponseClient, ConsumerProducerRequestResponseService, ProducerSenderClient, ResponseProducerIdNotFound}
import io.jobial.scase.core.{MessageHandler, MessageProducer, ReceiverClient, RequestHandler, RequestResponseClient, SenderClient, ServiceConfiguration}
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}
import io.jobial.scase.util.Hash.uuid

import javax.jms.{Destination, Session}


class JMSMessageHandlerServiceConfiguration[REQ: Marshaller : Unmarshaller](
  val serviceName: String,
  requestDestination: Destination
) {

  def service[F[_] : Concurrent](messageHandler: MessageHandler[F, REQ])(
    implicit session: Session
  ) = ???

  def client[F[_] : Concurrent : Timer](
    implicit session: Session
  ): F[SenderClient[F, REQ]] = ???
}

class JMSMessageSourceServiceConfiguration[REQ : Unmarshaller](
  sourceDestination: Destination
) {
  def client[F[_] : Concurrent : Timer](
    implicit session: Session
  ): F[ReceiverClient[F, REQ]] = ???
}


class JMSRequestResponseServiceConfiguration[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
  val serviceName: String,
  requestDestination: Destination,
  createDestination: (Session, String) => Destination,
  nameFromDestination: Destination => String
)(
  //implicit monitoringPublisher: MonitoringPublisher = noPublisher
  implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
  responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
) extends ServiceConfiguration {

  def service[F[_] : Concurrent](requestHandler: RequestHandler[F, REQ, RESP])(
    implicit session: Session
  ) =
    for {
      consumer <- JMSConsumer[F, REQ](requestDestination)
      service <- ConsumerProducerRequestResponseService[F, REQ, RESP](
        consumer, { responseDestinationName =>
          for {
            destination <-
              responseDestinationName match {
                case Some(responseDestinationName) =>
                  Concurrent[F].delay(createDestination(session, responseDestinationName))
                case None =>
                  Concurrent[F].raiseError(ResponseProducerIdNotFound("Not found response producer id in request"))
              }
            producer <- JMSProducer[F, Either[Throwable, RESP]](destination)
          } yield producer
        }: Option[String] => F[MessageProducer[F, Either[Throwable, RESP]]],
        requestHandler,
        defaultProducerId = None,
        autoCommitRequest = false,
        autoCommitFailedRequest = false
      )
    } yield service

  def client[F[_] : Concurrent : Timer](
    implicit session: Session
  ): F[RequestResponseClient[F, REQ, RESP]] =
    for {
      producer <- JMSProducer[F, REQ](requestDestination)
      responseDestinationName = s"${nameFromDestination(requestDestination)}-${uuid(6)}-response"
      consumer <- JMSConsumer[F, Either[Throwable, RESP]](
        createDestination(session, responseDestinationName)
      )
      client <- ConsumerProducerRequestResponseClient[F, REQ, RESP](
        consumer,
        () => producer,
        Some(responseDestinationName),
        autoCommitResponse = false
      )
    } yield client

}

class JMSStreamServiceConfiguration[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
  val serviceName: String,
  val requestDestination: Destination,
  val responseDestination: Destination
)(
  //implicit monitoringPublisher: MonitoringPublisher = noPublisher
  implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
  responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
) extends ServiceConfiguration {

  def service[F[_] : Concurrent](requestHandler: RequestHandler[F, REQ, RESP])(
    implicit session: Session
  ) =
    for {
      consumer <- JMSConsumer[F, REQ](requestDestination)
      service <- ConsumerProducerRequestResponseService[F, REQ, RESP](
        consumer, { _ =>
          for {
            producer <- JMSProducer[F, Either[Throwable, RESP]](responseDestination)
          } yield producer
        }: Option[String] => F[MessageProducer[F, Either[Throwable, RESP]]],
        requestHandler,
        defaultProducerId = None,
        autoCommitRequest = false,
        autoCommitFailedRequest = false
      )
    } yield service

  def client[F[_] : Concurrent : Timer](
    implicit session: Session
  ): F[SenderClient[F, REQ]] =
    for {
      producer <- JMSProducer[F, REQ](requestDestination)
      client <- ProducerSenderClient[F, REQ](
        producer
        //autoCommitResponse = false
      )
    } yield client
}

object JMSServiceConfiguration {

  def apply[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
    serviceName: String,
    requestDestination: Destination,
    createDestination: (Session, String) => Destination,
    nameFromDestination: Destination => String
  )(
    //implicit monitoringPublisher: MonitoringPublisher = noPublisher
    implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
    responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
  ): JMSRequestResponseServiceConfiguration[REQ, RESP] =
    new JMSRequestResponseServiceConfiguration[REQ, RESP](
      serviceName,
      requestDestination,
      createDestination,
      nameFromDestination
    )

  def apply[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
    serviceName: String,
    requestDestination: Destination,
    responseDestination: Destination
  )(
    //implicit monitoringPublisher: MonitoringPublisher = noPublisher
    implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
    responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
  ): JMSStreamServiceConfiguration[REQ, RESP] =
    new JMSStreamServiceConfiguration[REQ, RESP](
      serviceName,
      requestDestination,
      responseDestination
    )

  def apply[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
    serviceName: String,
    requestDestination: Destination
  )(
    //implicit monitoringPublisher: MonitoringPublisher = noPublisher
    implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
    responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
  ): JMSRequestResponseServiceConfiguration[REQ, RESP] = {
    val responseDestinationName = s"$requestDestination-${uuid(6)}-response"

    new JMSRequestResponseServiceConfiguration[REQ, RESP](
      serviceName,
      requestDestination,
      { (session, destinationName) =>
        // create response queue based on the request destination; it assumes that destination can be turned into a name 
        // and the underlying JMS implementation creates queues automatically; neither of these are part of the JMS standard
        // but it works for some impls (ActiveMQ, for example)
        if (destinationName === requestDestination.toString)
          session.createQueue(responseDestinationName)
        else
          session.createQueue(destinationName)
      },
      { destination =>
        destination.toString
      }
    )
  }

  def source[M: Unmarshaller]: JMSMessageSourceServiceConfiguration[M] = ???
}