package io.jobial.scase.core.impl

import cats.effect.Concurrent
import cats.effect.concurrent.{Deferred, Ref}
import cats.implicits._
import cats.{Monad, MonadError}
import io.jobial.scase.core.SendMessageContext
import io.jobial.scase.core.{CorrelationIdKey, MessageConsumer, MessageProducer, MessageReceiveResult, MessageSendResult, MessageSubscription, RequestContext, RequestHandler, RequestResponseMapping, SendResponseResult, Service, ServiceState}
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}
import scala.concurrent.duration.Duration


class ConsumerProducerRequestResponseService[F[_] : Concurrent, REQ, RESP: Marshaller](
  val responseProducersCacheRef: Option[Ref[F, Map[Option[String], MessageProducer[F, Either[Throwable, RESP]]]]],
  val requestConsumer: MessageConsumer[F, REQ],
  val responseProducer: Option[String] => F[MessageProducer[F, Either[Throwable, RESP]]],
  val requestHandler: RequestHandler[F, REQ, RESP],
  val autoCommitRequest: Boolean,
  val autoCommitFailedRequest: Boolean,
  val defaultProducerId: Option[String]
)(
  implicit val requestUnmarshaller: Unmarshaller[REQ],
  responseMarshaller: Marshaller[Either[Throwable, RESP]]
) extends DefaultService[F] with ConsumerProducerService[F, REQ, RESP] with Logging {

  override def sendResult(request: MessageReceiveResult[F, REQ], response: Deferred[F, Either[Throwable, RESP]], responseAttributes: Map[String, String]): F[MessageSendResult[F, _]] =
    for {
      res <- response.get
      producer <- responseProducersCacheRef match {
        case Some(producersCacheRef) =>
          // Producers are cached...
          for {
            producerCache <- producersCacheRef.get
            producer <- producerCache.get(request.responseProducerId) match {
              case Some(producer) =>
                pure(producer)
              case None =>
                responseProducer(request.responseProducerId)
            }
            _ <- producersCacheRef.update {
              producersCache =>
                producersCache + (request.responseProducerId -> producer)
            }
          } yield producer
        case None =>
          // Just call the provided function for a new producer...
          responseProducer(request.responseProducerId)
      }
      _ <- trace(s"found response producer $producer for request in service: ${request.toString.take(500)}")
      // TODO: make this a Deferred
      resultAfterSend <- res match {
        case Right(r) =>
          for {
            _ <- trace(s"sending success for request: ${request.toString.take(500)} on $producer")
            sendResult <- producer.send(Right(r), responseAttributes)
            // commit request after result is written
            _ <- whenA(autoCommitRequest)(
              trace(s"service committing request: ${request.toString.take(500)} on $producer") >>
                request.commit
            )
          } yield sendResult
        case Left(t) =>
          for {
            _ <- error(s"sending failure for request: ${request.toString.take(500)}", t)
            sendResult <- producer.send(Left(t), responseAttributes)
            _ <- whenA(autoCommitFailedRequest)(
              trace(s"service committing request: ${request.toString.take(500)}") >>
                request.commit
            )
          } yield sendResult
      }
    } yield resultAfterSend


}

object ConsumerProducerRequestResponseService extends CatsUtils with Logging {

  def apply[F[_] : Concurrent, REQ: Unmarshaller, RESP: Marshaller](
    requestConsumer: MessageConsumer[F, REQ],
    responseProducer: Option[String] => F[MessageProducer[F, Either[Throwable, RESP]]],
    requestHandler: RequestHandler[F, REQ, RESP],
    errorProducer: Option[Option[String] => F[MessageProducer[F, Either[Throwable, RESP]]]] = None, // TODO: implement this
    autoCommitRequest: Boolean = true,
    autoCommitFailedRequest: Boolean = true,
    reuseProducers: Boolean = true,
    defaultProducerId: Option[String] = None
  )(
    implicit responseMarshaller: Marshaller[Either[Throwable, RESP]]
    //sourceContext: SourceContext
  ): F[ConsumerProducerRequestResponseService[F, REQ, RESP]] = {
    def createProducerCache =
      if (reuseProducers)
        Ref.of[F, Map[Option[String], MessageProducer[F, Either[Throwable, RESP]]]](Map()).map(Some(_))
      else
        pure(None)

    for {
      responseProducersCacheRef <- createProducerCache
    } yield new ConsumerProducerRequestResponseService(
      responseProducersCacheRef,
      requestConsumer,
      responseProducer,
      requestHandler,
      autoCommitRequest,
      autoCommitFailedRequest,
      defaultProducerId
    )
  }
}
