package io.jobial.scase.tibrv

import cats.effect.Concurrent
import cats.effect.IO
import cats.effect.LiftIO
import cats.implicits._
import io.jobial.scase.core.MessageHandler
import io.jobial.scase.core.MessageProducer
import io.jobial.scase.core.ReceiverClient
import io.jobial.scase.core.RequestHandler
import io.jobial.scase.core.RequestResponseClient
import io.jobial.scase.core.SenderClient
import io.jobial.scase.core.ServiceConfiguration
import io.jobial.scase.core.impl.CatsUtils
import io.jobial.scase.core.impl.ConcurrentEffect
import io.jobial.scase.core.impl.ConsumerMessageHandlerService
import io.jobial.scase.core.impl.ConsumerProducerRequestResponseService
import io.jobial.scase.core.impl.ConsumerProducerStreamService
import io.jobial.scase.core.impl.ConsumerReceiverClient
import io.jobial.scase.core.impl.ProducerSenderClient
import io.jobial.scase.core.impl.TemporalEffect
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshaller
import io.jobial.scase.marshalling.Unmarshaller
import io.jobial.scase.tibrv.TibrvServiceConfiguration.destination
import io.jobial.scase.tibrv.TibrvServiceConfiguration.source
import scala.concurrent.duration._

class TibrvMessageHandlerServiceConfiguration[M: Marshaller : Unmarshaller](
  val serviceName: String,
  val subjects: Seq[String]
) extends ServiceConfiguration with CatsUtils with Logging {

  def service[F[_] : TemporalEffect : LiftIO](messageHandler: MessageHandler[F, M])(
    implicit context: TibrvContext,
    ioConcurrent: Concurrent[IO]
  ) =
    for {
      consumer <- TibrvConsumer[F, M](
        subjects
      )
      service = new ConsumerMessageHandlerService(
        consumer,
        messageHandler
      )
    } yield service

  def client[F[_] : ConcurrentEffect](
    implicit context: TibrvContext
  ) = destination[M](subjects.head).client[F]
}

class TibrvRequestResponseServiceConfiguration[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
  val serviceName: String,
  val subjects: Seq[String]
)(
  //implicit monitoringPublisher: MonitoringPublisher = noPublisher
  implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
  responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]],
  ioConcurrent: Concurrent[IO]
) extends ServiceConfiguration with CatsUtils with Logging {

  def service[F[_] : TemporalEffect : LiftIO](requestHandler: RequestHandler[F, REQ, RESP])(
    implicit context: TibrvContext
  ) =
    for {
      consumer <- TibrvConsumer[F, REQ](subjects)
      service <- TibrvRequestResponseService[F, REQ, RESP](
        consumer,
        requestHandler
      )
    } yield service

  def client[F[_] : ConcurrentEffect](
    implicit context: TibrvContext
  ): F[RequestResponseClient[F, REQ, RESP]] =
    TibrvRequestResponseClient[F, REQ, RESP](subjects.head)

}

class TibrvStreamServiceConfiguration[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
  val serviceName: String,
  val requestSubjects: Seq[String],
  val responseSubject: String
)(
  //implicit monitoringPublisher: MonitoringPublisher = noPublisher
  implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
  responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]],
  ioConcurrent: Concurrent[IO]
) extends ServiceConfiguration with CatsUtils {

  def service[F[_] : TemporalEffect : LiftIO](requestHandler: RequestHandler[F, REQ, RESP])(implicit context: TibrvContext) =
    for {
      consumer <- TibrvConsumer[F, REQ](requestSubjects)
      service <- ConsumerProducerRequestResponseService[F, REQ, RESP](
        consumer, { responseSubjectFromMessage =>
          TibrvProducer[F, Either[Throwable, RESP]](responseSubject).map(p => p: MessageProducer[F, Either[Throwable, RESP]])
        }: Option[String] => F[MessageProducer[F, Either[Throwable, RESP]]],
        requestHandler
      )
    } yield service


  def senderClient[F[_] : ConcurrentEffect](
    implicit context: TibrvContext
  ) = destination[REQ](requestSubjects.head).client[F]

  def receiverClient[F[_] : TemporalEffect : LiftIO](
    implicit context: TibrvContext
  ) = source[Either[Throwable, RESP]](
    Seq(responseSubject)
  ).client[F]

}

class TibrvStreamServiceWithErrorSubjectConfiguration[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
  val serviceName: String,
  val requestSubjects: Seq[String],
  val responseSubject: String,
  val errorSubject: String
)(
  //implicit monitoringPublisher: MonitoringPublisher = noPublisher
  implicit errorMarshaller: Marshaller[Throwable],
  errorUnmarshaller: Unmarshaller[Throwable],
  ioConcurrent: Concurrent[IO]
) extends ServiceConfiguration {

  def service[F[_] : TemporalEffect : LiftIO](requestHandler: RequestHandler[F, REQ, RESP])(
    implicit context: TibrvContext
  ) =
    for {
      consumer <- TibrvConsumer[F, REQ](requestSubjects)
      service <- ConsumerProducerStreamService[F, REQ, RESP](
        consumer, { _ =>
          for {
            producer <- TibrvProducer[F, RESP](responseSubject)
          } yield producer
        }: Option[String] => F[MessageProducer[F, RESP]], { _ =>
          for {
            producer <- TibrvProducer[F, Throwable](errorSubject)
          } yield producer
        }: Option[String] => F[MessageProducer[F, Throwable]],
        requestHandler,
        defaultProducerId = None,
        autoCommitRequest = false,
        autoCommitFailedRequest = false
      )
    } yield service

  def senderClient[F[_] : ConcurrentEffect](
    implicit context: TibrvContext
  ) = destination[REQ](requestSubjects.head).client[F]

  def responseReceiverClient[F[_] : TemporalEffect : LiftIO](
    implicit context: TibrvContext
  ) = source[RESP](Seq(responseSubject)).client[F]

  def errorReceiverClient[F[_] : TemporalEffect : LiftIO](
    implicit context: TibrvContext
  ) = source[Throwable](Seq(errorSubject)).client[F]
}

class TibrvMessageSourceServiceConfiguration[M: Unmarshaller](
  val subjects: Seq[String]
)(implicit ioConcurrent: Concurrent[IO]) {
  def client[F[_] : TemporalEffect : LiftIO](
    implicit context: TibrvContext
  ): F[ReceiverClient[F, M]] =
    for {
      consumer <- TibrvConsumer[F, M](
        subjects
      )
      client <- ConsumerReceiverClient[F, M](consumer)
    } yield client
}

class TibrvMessageDestinationServiceConfiguration[M: Marshaller](
  val subject: String
) {

  def client[F[_] : ConcurrentEffect](
    implicit context: TibrvContext
  ): F[SenderClient[F, M]] =
    for {
      producer <- TibrvProducer[F, M](subject)
      client <- ProducerSenderClient[F, M](
        producer
      )
    } yield client
}

object TibrvServiceConfiguration {

  def requestResponse[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
    requestSubjects: Seq[String]
  )(
    //implicit monitoringPublisher: MonitoringPublisher = noPublisher
    implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
    responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]],
    ioConcurrent: Concurrent[IO]
  ): TibrvRequestResponseServiceConfiguration[REQ, RESP] =
    new TibrvRequestResponseServiceConfiguration[REQ, RESP](
      requestSubjects.head,
      requestSubjects
    )

  def stream[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
    requestSubjects: Seq[String],
    responseSubject: String
  )(
    //implicit monitoringPublisher: MonitoringPublisher = noPublisher
    implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
    responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]],
    ioConcurrent: Concurrent[IO]
  ): TibrvStreamServiceConfiguration[REQ, RESP] =
    new TibrvStreamServiceConfiguration[REQ, RESP](
      requestSubjects.head,
      requestSubjects,
      responseSubject
    )

  def stream[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
    requestSubject: String,
    responseSubject: String
  )(
    //implicit monitoringPublisher: MonitoringPublisher = noPublisher
    implicit responseMarshaller: Marshaller[Either[Throwable, RESP]],
    responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]],
    ioConcurrent: Concurrent[IO]
  ): TibrvStreamServiceConfiguration[REQ, RESP] =
    stream[REQ, RESP](
      requestSubject,
      responseSubject
    )

  def stream[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
    requestSubjects: Seq[String],
    responseSubject: String,
    errorSubject: String
  )(
    //implicit monitoringPublisher: MonitoringPublisher = noPublisher
    implicit errorMarshaller: Marshaller[Throwable],
    errorUnmarshaller: Unmarshaller[Throwable],
    ioConcurrent: Concurrent[IO]
  ) = new TibrvStreamServiceWithErrorSubjectConfiguration[REQ, RESP](
    requestSubjects.head,
    requestSubjects,
    responseSubject,
    errorSubject
  )

  def handler[M: Marshaller : Unmarshaller](
    requestSubjects: Seq[String]
  ) = new TibrvMessageHandlerServiceConfiguration[M](
    requestSubjects.head,
    requestSubjects
  )

  def source[M: Unmarshaller](
    sourceSubjects: Seq[String]
  )(implicit ioConcurrent: Concurrent[IO]) = new TibrvMessageSourceServiceConfiguration(
    sourceSubjects
  )

  def destination[M: Marshaller](
    subject: String,
    batchingMaxPublishDelay: Option[FiniteDuration] = Some(1.millis)
  ) = new TibrvMessageDestinationServiceConfiguration[M](subject)
}