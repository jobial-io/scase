package io.jobial.scase.jms

import cats.effect.Concurrent
import cats.effect.Timer
import cats.implicits._
import io.jobial.scase.core.impl.CatsUtils
import io.jobial.scase.core.impl.ConsumerMessageHandlerService
import io.jobial.scase.core.impl.ConsumerProducerRequestResponseClient
import io.jobial.scase.core.impl.ConsumerProducerRequestResponseService
import io.jobial.scase.core.impl.ConsumerReceiverClient
import io.jobial.scase.core.impl.ProducerSenderClient
import io.jobial.scase.core.impl.ResponseProducerIdNotFound
import io.jobial.scase.core.MessageHandler
import io.jobial.scase.core.MessageProducer
import io.jobial.scase.core.ReceiverClient
import io.jobial.scase.core.RequestHandler
import io.jobial.scase.core.RequestResponseClient
import io.jobial.scase.core.SenderClient
import io.jobial.scase.core.ServiceConfiguration
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshaller
import io.jobial.scase.marshalling.Unmarshaller
import io.jobial.scase.util.Hash.uuid
import javax.jms.Destination
import javax.jms.Session

class JMSRequestResponseServiceConfiguration[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
  val serviceName: String,
  requestDestination: Destination,
  createDestination: (Session, String) => Destination,
  nameFromDestination: Destination => String
)(
  //implicit monitoringPublisher: MonitoringPublisher = noPublisher
  implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
  responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
) extends ServiceConfiguration with CatsUtils with Logging {

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
                  raiseError(ResponseProducerIdNotFound("Not found response producer id in request"))
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

class JMSMessageHandlerServiceConfiguration[M: Marshaller : Unmarshaller](
  val serviceName: String,
  requestDestination: Destination
) {

  def service[F[_] : Concurrent](messageHandler: MessageHandler[F, M])(
    implicit session: Session
  ) =
    for {
      consumer <- JMSConsumer[F, M](requestDestination)
      service = new ConsumerMessageHandlerService(
        consumer,
        messageHandler
      )
    } yield service

  def client[F[_] : Concurrent : Timer](
    implicit session: Session
  ): F[SenderClient[F, M]] =
    for {
      producer <- JMSProducer[F, M](requestDestination)
      client <- ProducerSenderClient[F, M](
        producer
      )
    } yield client
}

class JMSMessageSourceServiceConfiguration[M: Unmarshaller](
  sourceDestination: Destination
) {
  def client[F[_] : Concurrent : Timer](
    implicit session: Session
  ): F[ReceiverClient[F, M]] =
    for {
      consumer <- JMSConsumer[F, M](sourceDestination)
      client <- ConsumerReceiverClient[F, M](consumer)
    } yield client
}

class JMSMessageDestinationServiceConfiguration[M: Marshaller](
  requestDestination: Destination
) {

  def client[F[_] : Concurrent : Timer](
    implicit session: Session
  ): F[SenderClient[F, M]] =
    for {
      producer <- JMSProducer[F, M](requestDestination)
      client <- ProducerSenderClient[F, M](
        producer
      )
    } yield client
}


object JMSServiceConfiguration {

  def requestResponse[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
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

  def stream[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
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
    
  private def stripScheme(uri: String) =
    uri.substring(uri.lastIndexOf('/') + 1)

  def requestResponse[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
    serviceName: String,
    requestDestination: Destination
  )(
    //implicit monitoringPublisher: MonitoringPublisher = noPublisher
    implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
    responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
  ): JMSRequestResponseServiceConfiguration[REQ, RESP] = {
    val requestDestinationName = requestDestination.toString
    val responseDestinationName = s"${stripScheme(requestDestinationName)}-${uuid(6)}-response"

    new JMSRequestResponseServiceConfiguration[REQ, RESP](
      serviceName,
      requestDestination,
      { (session, destinationName) =>
        // create response queue based on the request destination; it assumes that destination can be turned into a name 
        // and the underlying JMS implementation creates queues automatically; neither of these are part of the JMS standard
        // but it works for some impls (ActiveMQ, for example)
        if (destinationName === requestDestination.toString)
          session.createQueue(stripScheme(responseDestinationName))
        else
          session.createQueue(stripScheme(destinationName))
      },
      { destination =>
        destination.toString
      }
    )
  }

  def handler[M: Marshaller : Unmarshaller](serviceName: String, requestDestination: Destination) =
    new JMSMessageHandlerServiceConfiguration[M](serviceName, requestDestination)

  def source[M: Unmarshaller](sourceDestination: Destination) =
    new JMSMessageSourceServiceConfiguration[M](sourceDestination)

  def destination[M: Marshaller](requestDestination: Destination) =
    new JMSMessageDestinationServiceConfiguration[M](requestDestination)

}