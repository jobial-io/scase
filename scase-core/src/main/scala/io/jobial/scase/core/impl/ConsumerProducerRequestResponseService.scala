package io.jobial.scase.core.impl

import cats.effect.Concurrent
import cats.effect.concurrent.Deferred
import cats.effect.concurrent.Ref
import cats.implicits._
import io.jobial.scase.core.MessageConsumer
import io.jobial.scase.core.MessageProducer
import io.jobial.scase.core.MessageReceiveResult
import io.jobial.scase.core.MessageSendResult
import io.jobial.scase.core.RequestHandler
import io.jobial.scase.core.SendResponseResult
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshaller
import io.jobial.scase.marshalling.Unmarshaller


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
) extends DefaultService[F] with ConsumerProducerService[F, REQ, RESP] {

  override def sendResult(request: MessageReceiveResult[F, REQ], responseDeferred: Deferred[F, SendResponseResult[RESP]]): F[MessageSendResult[F, _]] =
    for {
      response <- responseDeferred.get
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
      resultAfterSend <- response.response match {
        case Right(r) =>
          for {
            _ <- trace(s"sending success for request: ${request.toString.take(500)} on $producer")
            sendResult <- producer.send(Right(r), response.sendMessageContext.attributes)
            // commit request after result is written
            _ <- whenA(autoCommitRequest)(
              trace(s"service committing request: ${request.toString.take(500)} on $producer") >>
                request.commit
            )
          } yield sendResult
        case Left(t) =>
          for {
            _ <- error(s"sending failure for request: ${request.toString.take(500)}", t)
            sendResult <- producer.send(Left(t), response.sendMessageContext.attributes)
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
