package io.jobial.scase.core.impl

import cats.Monad
import cats.effect.Concurrent
import cats.effect.concurrent.Deferred
import cats.effect.concurrent.Ref
import cats.implicits._
import io.jobial.scase.core.MessageConsumer
import io.jobial.scase.core.MessageProducer
import io.jobial.scase.core.MessageReceiveResult
import io.jobial.scase.core.RequestHandler
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshaller
import io.jobial.scase.marshalling.Unmarshaller


class ConsumerProducerStreamService[F[_] : Concurrent, REQ, RESP: Marshaller](
  val responseProducersCacheRef: Option[Ref[F, Map[Option[String], MessageProducer[F, RESP]]]],
  val errorProducersCacheRef: Option[Ref[F, Map[Option[String], MessageProducer[F, Throwable]]]],
  val requestConsumer: MessageConsumer[F, REQ],
  val responseProducer: Option[String] => F[MessageProducer[F, RESP]],
  val errorProducer: Option[String] => F[MessageProducer[F, Throwable]],
  val requestHandler: RequestHandler[F, REQ, RESP],
  val autoCommitRequest: Boolean,
  val autoCommitFailedRequest: Boolean,
  val defaultProducerId: Option[String]
)(
  implicit errorMarshaller: Marshaller[Throwable],
  val requestUnmarshaller: Unmarshaller[REQ]
) extends DefaultService[F] with ConsumerProducerService[F, REQ, RESP] with Logging {

  def sendResult(request: MessageReceiveResult[F, REQ], response: Deferred[F, Either[Throwable, RESP]], responseAttributes: Map[String, String]) =
    for {
      res <- response.get
      // TODO: make this a Deferred
      resultAfterSend <- res match {
        case Right(r) =>
          for {
            producer <- responseProducersCacheRef match {
              case Some(producersCacheRef) =>
                // Producers are cached...
                for {
                  producerCache <- producersCacheRef.get
                  producer <- producerCache.get(request.responseProducerId) match {
                    case Some(producer) =>
                      Monad[F].pure(producer)
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
            _ <- debug[F](s"sending success for request: ${request.toString.take(500)} on $producer")
            _ <- debug[F](s"found response producer $producer for request in service: ${request.toString.take(500)}")
            sendResult <- producer.send(r, responseAttributes)
            // commit request after result is written
            _ <- if (autoCommitRequest) {
              debug[F](s"service committing request: ${request.toString.take(500)} on $producer") >>
                request.commit
            } else Monad[F].unit
          } yield sendResult
        case Left(t) =>
          for {
            _ <- error[F](s"sending failure for request: ${request.toString.take(500)}", t)
            producer <- errorProducersCacheRef match {
              case Some(producersCacheRef) =>
                // Producers are cached...
                for {
                  producerCache <- producersCacheRef.get
                  producer <- producerCache.get(request.responseProducerId) match {
                    case Some(producer) =>
                      Monad[F].pure(producer)
                    case None =>
                      errorProducer(request.responseProducerId)
                  }
                  _ <- producersCacheRef.update {
                    producersCache =>
                      producersCache + (request.responseProducerId -> producer)
                  }
                } yield producer
              case None =>
                // Just call the provided function for a new producer...
                errorProducer(request.responseProducerId)
            }
            _ <- debug[F](s"found response producer $producer for request in service: ${request.toString.take(500)}")
            sendResult <- producer.send(t, responseAttributes)
            _ <-
              if (autoCommitFailedRequest)
                debug[F](s"service committing request: ${request.toString.take(500)}") >>
                  request.commit
              else Monad[F].unit
          } yield sendResult
      }
    } yield resultAfterSend


}

object ConsumerProducerStreamService {

  def apply[F[_] : Concurrent, REQ: Unmarshaller, RESP: Marshaller](
    requestConsumer: MessageConsumer[F, REQ],
    responseProducer: Option[String] => F[MessageProducer[F, RESP]],
    errorProducer: Option[String] => F[MessageProducer[F, Throwable]],
    requestHandler: RequestHandler[F, REQ, RESP],
    autoCommitRequest: Boolean = true,
    autoCommitFailedRequest: Boolean = true,
    reuseProducers: Boolean = true,
    defaultProducerId: Option[String] = None
  )(
    implicit errorMarshaller: Marshaller[Throwable]
  ) = {
    def createProducerCache[R] =
      if (reuseProducers)
        Ref.of[F, Map[Option[String], MessageProducer[F, R]]](Map()).map(Some(_))
      else
        Monad[F].pure(None)

    for {
      responseProducersCacheRef <- createProducerCache[RESP]
      errorProducersCacheRef <- createProducerCache[Throwable]
    } yield new ConsumerProducerStreamService(
      responseProducersCacheRef,
      errorProducersCacheRef,
      requestConsumer,
      responseProducer,
      errorProducer,
      requestHandler,
      autoCommitRequest,
      autoCommitFailedRequest,
      defaultProducerId
    )
  }
}

