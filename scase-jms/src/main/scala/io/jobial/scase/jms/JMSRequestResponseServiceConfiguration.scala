package io.jobial.scase.jms

import cats.effect.{Concurrent, Timer}
import cats.implicits._
import io.jobial.scase.core.impl.{ConsumerProducerRequestResponseClient, ConsumerProducerRequestResponseService, ResponseProducerIdNotFound}
import io.jobial.scase.core.{MessageProducer, RequestHandler, RequestResponseClient, ServiceConfiguration}
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}
import io.jobial.scase.util.Hash.uuid

import javax.jms.{Destination, Session}
import scala.concurrent.duration.{Duration, DurationInt}


class JMSRequestResponseServiceConfiguration[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
  val serviceName: String,
  requestDestination: Destination,
  responseDestination: Option[Destination],
  createDestination: Option[(Session, String) => Destination],
  nameFromDestination: Option[Destination => String]
)(
  //implicit monitoringPublisher: MonitoringPublisher = noPublisher
  implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
  responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
) extends ServiceConfiguration {

  assert(responseDestination.isDefined || createDestination.isDefined && nameFromDestination.isDefined,
    "Either responseDestination or createDestination and nameFromDestination have to be defined")

  def service[F[_] : Concurrent](requestHandler: RequestHandler[F, REQ, RESP])(
    implicit session: Session
  ) =
    for {
      consumer <- JMSConsumer[F, REQ](requestDestination)
      service <- ConsumerProducerRequestResponseService[F, REQ, RESP](
        consumer, { responseDestinationName =>
          for {
            destination <- responseDestination.map(Concurrent[F].delay(_)).getOrElse {
              responseDestinationName match {
                case Some(responseDestinationName) =>
                  Concurrent[F].delay(createDestination.map(_ (session, responseDestinationName)).getOrElse(???))
                case None =>
                  Concurrent[F].raiseError(ResponseProducerIdNotFound("Not found response producer id in request"))
              }
            }
            producer <- JMSProducer[F, Either[Throwable, RESP]](destination)
          } yield producer
        }: Option[String] => F[MessageProducer[F, Either[Throwable, RESP]]],
        requestHandler,
        defaultProducerId = responseDestination.map(_ => ""),
        autoCommitRequest = false,
        autoCommitFailedRequest = false
      )
    } yield service

  def client[F[_] : Concurrent : Timer](
    implicit session: Session
  ): F[RequestResponseClient[F, REQ, RESP]] =
    for {
      producer <- JMSProducer[F, REQ](requestDestination)
      responseDestinationName =
        for {
          nameFromDestination <- nameFromDestination
        } yield
          s"${nameFromDestination(requestDestination)}-${uuid(6)}-response"
      consumer <- JMSConsumer[F, Either[Throwable, RESP]](
        responseDestination
          .orElse {
            for {
              createDestination <- createDestination
              nameFromDestination <- nameFromDestination
              responseDestinationName <- responseDestinationName
            } yield
              createDestination(session, responseDestinationName)
          }.getOrElse(???)
      )
      client <- ConsumerProducerRequestResponseClient[F, REQ, RESP](
        consumer,
        () => producer,
        responseDestinationName,
        autoCommitResponse = false
      )
    } yield client

}

object JMSRequestResponseServiceConfiguration {
  
  def apply[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
    serviceName: String,
    requestDestination: Destination,
    responseDestination: Option[Destination],
    createDestination: Option[(Session, String) => Destination],
    nameFromDestination: Option[Destination => String]
  )(
    //implicit monitoringPublisher: MonitoringPublisher = noPublisher
    implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
    responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
  ): JMSRequestResponseServiceConfiguration[REQ, RESP] =
    new JMSRequestResponseServiceConfiguration[REQ, RESP](
      serviceName,
      requestDestination,
      responseDestination,
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
  ): JMSRequestResponseServiceConfiguration[REQ, RESP] =
    new JMSRequestResponseServiceConfiguration[REQ, RESP](
      serviceName,
      requestDestination,
      Some(responseDestination),
      None,
      None
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
      responseDestination = None,
      Some { (session, destinationName) =>
        // create response queue based on the request destination; it assumes that destination can be turned into a name 
        // and the underlying JMS implementation creates queues automatically; neither of these are part of the JMS standard
        // but it works for some impls (ActiveMQ, for example)
        if (destinationName === requestDestination.toString)
          session.createQueue(responseDestinationName)
        else
          session.createQueue(destinationName)
      },
      Some { destination =>
        destination.toString
      }
    )
  }

}