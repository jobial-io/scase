package io.jobial.scase.core.impl

import cats.effect.Sync
import cats.effect.concurrent.Deferred
import cats.implicits._
import io.jobial.scase.core.SendMessageContext
import io.jobial.scase.core.CorrelationIdKey
import io.jobial.scase.core.MessageConsumer
import io.jobial.scase.core.MessageReceiveResult
import io.jobial.scase.core.MessageSendResult
import io.jobial.scase.core.MessageSubscription
import io.jobial.scase.core.RequestContext
import io.jobial.scase.core.RequestHandler
import io.jobial.scase.core.RequestResponseMapping
import io.jobial.scase.core.SendResponseResult
import io.jobial.scase.core.Service
import io.jobial.scase.core.ServiceState
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Unmarshaller
import scala.concurrent.duration.Duration


trait ConsumerProducerService[F[_], REQ, RESP] extends CatsUtils with Logging {
  this: DefaultService[F] =>

  val requestConsumer: MessageConsumer[F, REQ]

  val requestHandler: RequestHandler[F, REQ, RESP]

  val autoCommitRequest: Boolean

  val autoCommitFailedRequest: Boolean

  val defaultProducerId: Option[String]

  implicit def requestUnmarshaller: Unmarshaller[REQ]

  def handleRequest(request: MessageReceiveResult[F, REQ]) = start {
    for {
      _ <- trace(s"received request with producer id ${request.responseProducerId}: ${request.toString.take(500)}")
      response <- Deferred[F, SendResponseResult[RESP]]
      message <- request.message
      processorResult <- {
        val processorResult =
          delay(requestHandler.handleRequest(new RequestContext[F] {

            def reply[REQUEST, RESPONSE](req: REQUEST, r: Either[Throwable, RESPONSE])(
              implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE],
              sendMessageContext: SendMessageContext
            ): F[SendResponseResult[RESPONSE]] =
              trace(s"context sending response ${r.toString.take(500)}") >>
                pure(DefaultSendResponseResult(r, sendMessageContext))

            val requestTimeout = request.requestTimeout.getOrElse(Duration.Inf)

            def receiveResult[REQUEST](r: REQUEST): MessageReceiveResult[F, REQUEST] =
              request.asInstanceOf[MessageReceiveResult[F, REQUEST]]

          })(message)).flatten

        val responseAttributes = request.correlationId.map(correlationId => Map(CorrelationIdKey -> correlationId)).getOrElse(Map())

        val processResultWithErrorHandling = processorResult
          .flatMap {
            result =>
              (result.response match {
                case Right(r) =>
                  trace(s"request processing successful")
                case Left(t) =>
                  error(s"request processing failed: ${request.toString.take(500)}", t)
              }) >> response.complete(DefaultSendResponseResult(result.response, result.sendMessageContext.copy(attributes = responseAttributes ++ result.sendMessageContext.attributes)))
          }.handleErrorWith {
          case t =>
            error(s"request processing failed: ${request.toString.take(500)}", t) >>
              request.rollback >>
              response.complete(DefaultSendResponseResult(Left(t), SendMessageContext(responseAttributes)))
        }

        // TODO: handle handleRequest timeout

        // send response when ready
        processResultWithErrorHandling >>
          sendResult(request, response).map(_ => ()).handleErrorWith { t =>
            error(s"unhadled error", t)
          }
      }
    } yield processorResult
  }

  def sendResult(request: MessageReceiveResult[F, REQ], responseDeferred: Deferred[F, SendResponseResult[RESP]]): F[MessageSendResult[F, _]]

  def start: F[ServiceState[F]] =
    for {
      _ <- trace(s"starting service for processor $requestHandler on $requestConsumer")
      subscription <- requestConsumer.subscribe(handleRequest)
      _ <- trace(s"subscribed to consumer for processor $requestHandler")
    } yield
      new DefaultServiceState[F, REQ](subscription, requestConsumer, this)
}

class DefaultServiceState[F[_] : Sync, M](
  val subscription: MessageSubscription[F, M],
  val consumer: MessageConsumer[F, M],
  val service: Service[F]
) extends ServiceState[F]
  with Logging {

  def stop =
    for {
      _ <- subscription.cancel
      _ <- trace(s"shutting down $service...")
      _ <- subscription.join
      _ <- consumer.stop
    } yield this

  def join =
    for {
      _ <- subscription.join
    } yield this
}

case class DefaultSendResponseResult[RESPONSE](response: Either[Throwable, RESPONSE], sendMessageContext: SendMessageContext) extends SendResponseResult[RESPONSE]

case class ResponseProducerIdNotFound(message: String) extends IllegalStateException(message)