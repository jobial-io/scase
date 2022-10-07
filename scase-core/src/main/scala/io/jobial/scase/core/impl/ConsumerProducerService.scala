package io.jobial.scase.core.impl

import cats.effect.Concurrent
import cats.effect.IO.raiseError
import cats.effect.Sync
import cats.effect.concurrent.Deferred
import cats.effect.concurrent.{Deferred, Ref}
import cats.implicits._
import cats.{Monad, MonadError}
import io.jobial.scase.core.SendMessageContext
import io.jobial.scase.core.{CorrelationIdKey, MessageConsumer, MessageProducer, MessageReceiveResult, MessageSendResult, MessageSubscription, RequestContext, RequestHandler, RequestResponseMapping, SendResponseResult, Service, ServiceState}
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}
import scala.concurrent.duration.Duration


trait ConsumerProducerService[F[_], REQ, RESP] extends CatsUtils with Logging {
  this: DefaultService[F] =>

  val requestConsumer: MessageConsumer[F, REQ]

  val requestHandler: RequestHandler[F, REQ, RESP]

  val autoCommitRequest: Boolean

  val autoCommitFailedRequest: Boolean

  val defaultProducerId: Option[String]

  implicit def requestUnmarshaller: Unmarshaller[REQ]

  def handleRequest(request: MessageReceiveResult[F, REQ]) = {

    val r: F[MessageSendResult[F, _]] = {
      for {
        _ <- debug(s"received request in service: ${request.toString.take(500)}")
        _ <- debug(s"found response producer id ${request.responseProducerId} in request")
        response <- Deferred[F, Either[Throwable, RESP]]
        message <- request.message
        processorResult <- {
          val processorResult =
            delay(requestHandler.handleRequest(new RequestContext[F] {

              def reply[REQUEST, RESPONSE](req: REQUEST, r: RESPONSE)(
                implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE],
                sendMessageContext: SendMessageContext
              ): SendResponseResult[RESPONSE] = {
                logger.debug(s"context sending response ${r.toString.take(500)}")
                DefaultSendResponseResult[RESPONSE](r)
              }

              val requestTimeout = request.requestTimeout.getOrElse(Duration.Inf)

              def receiveResult[REQUEST](r: REQUEST): MessageReceiveResult[F, REQUEST] =
                request.asInstanceOf[MessageReceiveResult[F, REQUEST]]

            })(message)).flatten

          val processResultWithErrorHandling = processorResult
            .flatMap {
              result =>
                debug(s"request processing successful") >>
                  response.complete(Right(result.response))
            }
            .handleErrorWith {
              case t =>
                error(s"request processing failed: ${request.toString.take(500)}", t) >>
                  response.complete(Left(t))
            }

          // TODO: handle handleRequest timeout

          val responseAttributes = request.correlationId.map(correlationId => Map(CorrelationIdKey -> correlationId)).getOrElse(Map())

          // send response when ready
          start(processResultWithErrorHandling) >>
            sendResult(request, response, responseAttributes).handleErrorWith { t =>
              error(s"unhadled error", t) >> raiseError(t)
            }
        }
      } yield processorResult
    }
    r
  }

  def sendResult(request: MessageReceiveResult[F, REQ], response: Deferred[F, Either[Throwable, RESP]], responseAttributes: Map[String, String]): F[MessageSendResult[F, _]]

  def start: F[ServiceState[F]] =
    for {
      _ <- info(s"starting service for processor $requestHandler")
      subscription <- requestConsumer.subscribe(handleRequest)
      _ <- info(s"subscribed to consumer for processor $requestHandler")
    } yield
      DefaultServiceState(subscription, this)
}

case class DefaultServiceState[F[_] : Sync, M](
  subscription: MessageSubscription[F, M],
  service: Service[F]
) extends ServiceState[F]
  with Logging {

  def stop =
    for {
      _ <- subscription.cancel
      _ <- info(s"Shutting down $service...")
      _ <- subscription.join
    } yield this

  def join =
    for {
      _ <- subscription.join
    } yield this
}

case class DefaultSendResponseResult[RESPONSE](response: RESPONSE) extends SendResponseResult[RESPONSE]

case class ResponseProducerIdNotFound(message: String) extends IllegalStateException(message)