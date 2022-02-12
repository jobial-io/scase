package io.jobial.scase.core.impl

import cats.effect.Concurrent
import cats.effect.concurrent.{Deferred, Ref}
import cats.implicits._
import cats.{Monad, MonadError}
import io.jobial.scase.core.{CorrelationIdKey, MessageConsumer, MessageProducer, MessageReceiveResult, MessageSendResult, MessageSubscription, RequestContext, RequestHandler, RequestResponseMapping, SendResponseResult, Service, ServiceState}
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}

import scala.concurrent.duration.Duration


class ConsumerProducerRequestResponseService[F[_] : Concurrent, REQ: Unmarshaller, RESP: Marshaller](
  producersCacheRef: Option[Ref[F, Map[String, MessageProducer[F, Either[Throwable, RESP]]]]],
  messageConsumer: MessageConsumer[F, REQ],
  messageProducer: String => F[MessageProducer[F, Either[Throwable, RESP]]],
  requestHandler: RequestHandler[F, REQ, RESP],
  messageProducerForErrors: Option[MessageProducer[F, REQ]], // TODO: implement this
  autoCommitRequest: Boolean,
  autoCommitFailedRequest: Boolean
)(
  implicit responseMarshaller: Marshaller[Either[Throwable, RESP]]
  //sourceContext: SourceContext
) extends DefaultService[F] with Logging {

  private def handleRequest(request: MessageReceiveResult[F, REQ]) = {
    logger.debug(s"received request in service: ${request.toString.take(500)}")
    val r: F[MessageSendResult[F, Either[Throwable, RESP]]] =
      request.responseProducerId match {
        case Some(responseProducerId) =>
          logger.debug(s"found response producer id $responseProducerId in request")

          for {
            producer <- producersCacheRef match {
              case Some(producersCacheRef) =>
                // Producers are cached...
                for {
                  producerCache <- producersCacheRef.get
                  producer <- producerCache.get(responseProducerId) match {
                    case Some(producer) =>
                      Monad[F].pure(producer)
                    case None =>
                      messageProducer(responseProducerId)
                  }
                  _ <- producersCacheRef.update {
                    producersCache =>
                      producersCache + (responseProducerId -> producer)
                  }
                } yield producer
              case None =>
                // Just call the provided function for a new producer...
                messageProducer(responseProducerId)
            }
            response <- Deferred[F, Either[Throwable, RESP]]
            message <- request.message
            processorResult <- {
              logger.debug(s"found response producer $producer for request in service: ${request.toString.take(500)}")
              // TODO: make this a Deferred

              val processorResult =
                requestHandler.handleRequestOrFail(new RequestContext[F] {

                  def reply[REQUEST, RESPONSE](req: REQUEST, r: RESPONSE)
                    (implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE]): SendResponseResult[RESPONSE] = {
                    logger.debug(s"context sending response ${r.toString.take(500)}")
                    DefaultSendResponseResult[RESPONSE](r)
                  }

                  val requestTimeout = request.requestTimeout.getOrElse(Duration.Inf)

                }, Concurrent[F])(message)

              val processResultWithErrorHandling = processorResult
                .flatMap {
                  result =>
                    response.complete(Right(result.response))
                }
                .handleErrorWith {
                  case t =>
                    logger.error(s"request processing failed: ${request.toString.take(500)}", t)
                    response.complete(Left(t))
                }

              // TODO: handle handleRequest timeout

              val responseAttributes = request.correlationId.map(correlationId => Map(CorrelationIdKey -> correlationId)).getOrElse(Map())

              // send response when ready
              for {
                r <- processResultWithErrorHandling
                res <- response.get
                resultAfterSend <- res match {
                  case Right(r) =>
                    logger.debug(s"sending success to client for request: ${request.toString.take(500)} on $producer")
                    for {
                      sendResult <- producer.send(Right(r), responseAttributes)
                      // commit request after result is written
                      _ <- if (autoCommitRequest) {
                        logger.debug(s"service committing request: ${request.toString.take(500)} on $producer")
                        request.commit
                      } else Monad[F].unit
                    } yield sendResult
                  case Left(t) =>
                    logger.error(s"sending failure to client for request: ${request.toString.take(500)}", t)
                    for {
                      sendResult <- producer.send(Left(t), responseAttributes)
                      _ <- if (autoCommitFailedRequest) {
                        logger.debug(s"service committing request: ${request.toString.take(500)}")
                        request.commit
                      } else Monad[F].unit
                    } yield sendResult
                }
              } yield resultAfterSend
            }
          } yield processorResult
        case None =>
          logger.error(s"response consumer id not found for request: ${request.toString.take(500)}")
          MonadError[F, Throwable].raiseError(ResponseProducerIdNotFound())
      }

    r
  }

  def start = {
    logger.info(s"starting service for processor $requestHandler")

    for {
      subscription <- messageConsumer.subscribe(handleRequest)
    } yield {
      logger.info(s"started service for processor $requestHandler")
      DefaultServiceState(subscription, this)
    }
  }

}

case class DefaultServiceState[F[_] : Monad, M](
  subscription: MessageSubscription[F, M],
  service: Service[F]
) extends ServiceState[F]
  with Logging {

  def stop =
    for {
      r <- subscription.cancel
      _ <- subscription.join
    } yield {
      logger.info(s"Shutting down $service")
      //service.executionContext.shutdown
      this
    }

  def join =
    for {
      _ <- subscription.join
    } yield {
      this
    }
}

case class DefaultSendResponseResult[RESPONSE](response: RESPONSE) extends SendResponseResult[RESPONSE]

object ConsumerProducerRequestResponseService {

  def apply[F[_] : Concurrent, REQ: Unmarshaller, RESP: Marshaller](
    messageConsumer: MessageConsumer[F, REQ],
    messageProducer: String => F[MessageProducer[F, Either[Throwable, RESP]]],
    requestHandler: RequestHandler[F, REQ, RESP],
    messageProducerForErrors: Option[MessageProducer[F, REQ]] = None, // TODO: implement this
    autoCommitRequest: Boolean = true,
    autoCommitFailedRequest: Boolean = true,
    reuseProducers: Boolean = true
  )(
    implicit responseMarshaller: Marshaller[Either[Throwable, RESP]]
    //sourceContext: SourceContext
  ): F[ConsumerProducerRequestResponseService[F, REQ, RESP]] =
    for {
      producersCacheRef <-
        if (reuseProducers)
          Ref.of[F, Map[String, MessageProducer[F, Either[Throwable, RESP]]]](Map()).map(Some(_))
        else
          Concurrent[F].pure(None)
    } yield new ConsumerProducerRequestResponseService(
      producersCacheRef,
      messageConsumer,
      messageProducer,
      requestHandler,
      messageProducerForErrors,
      autoCommitRequest,
      autoCommitFailedRequest
    )
}

case class ResponseProducerIdNotFound() extends IllegalStateException