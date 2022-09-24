package io.jobial.scase.core.impl

import cats.effect.concurrent.{Deferred, Ref}
import cats.effect.implicits.catsEffectSyntaxConcurrent
import cats.effect.{Concurrent, Timer}
import cats.implicits._
import cats._
import io.jobial.scase.core.{CorrelationIdKey, DefaultMessageReceiveResult, MessageConsumer, MessageProducer, MessageReceiveResult, MessageSendResult, MessageSubscription, RequestResponseClient, RequestResponseMapping, RequestResponseResult, RequestTimeout, RequestTimeoutKey, ResponseProducerIdKey, SendRequestContext}
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}

import java.util.UUID.randomUUID
import scala.concurrent.TimeoutException
import scala.concurrent.duration.FiniteDuration

class ConsumerProducerRequestResponseClient[F[_] : Concurrent : Timer, REQ: Marshaller, RESP](
  correlationsRef: Ref[F, Map[String, CorrelationInfo[F, REQ, RESP]]],
  messageSubscription: MessageSubscription[F, Either[Throwable, RESP]],
  messageConsumer: MessageConsumer[F, Either[Throwable, RESP]],
  messageProducer: () => MessageProducer[F, REQ],
  responseProducerId: Option[String],
  autoCommitResponse: Boolean,
  name: String
)(
  implicit responseMarshaller: Unmarshaller[Either[Throwable, RESP]]
  //monitoringPublisher: MonitoringPublisher
) extends RequestResponseClient[F, REQ, RESP] with Logging {

  val holdOntoOutstandingRequest = true

  def logOutsanding =
    for {
      correlations <- correlationsRef.get
    } yield
      if (correlations.size > 0)
        logger.warn(s"Outstanding correlations: ${correlations} on ${this}")

  def sendRequestWithResponseMapping[REQUEST <: REQ, RESPONSE <: RESP](request: REQUEST, requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE])
    (implicit sendRequestContext: SendRequestContext) = {

    val producer = messageProducer()
    val correlationId = randomUUID.toString

    //monitoringPublisher.increment(request.getClass.getName)
    logger.info(s"sending ${request.toString.take(500)} with $correlationId using $this")

    for {
      receiveResultDeferred <- Deferred[F, MessageReceiveResult[F, Either[Throwable, RESP]]]
      _ <- correlationsRef.update { correlations =>
        correlations + ((correlationId, CorrelationInfo(
          receiveResultDeferred,
          System.currentTimeMillis,
          if (holdOntoOutstandingRequest)
            Some(request)
          else
            None
        )))
      }
      _ = logger.info(s"sending request with correlation id $correlationId")
      sendResult <- producer.send(
        request,
        Map(
          CorrelationIdKey -> correlationId
        ) ++ responseProducerId.map(responseProducerId => ResponseProducerIdKey -> responseProducerId) ++ sendRequestContext.requestTimeout.map(t => RequestTimeoutKey -> t.toMillis.toString)
      ).asInstanceOf[F[MessageSendResult[F, REQUEST]]]
      _ = logger.info(s"waiting for request with correlation id $correlationId")
      receiveResult <- sendRequestContext.requestTimeout match {
        case Some(requestTimeout) =>
          requestTimeout match {
            case requestTimeout: FiniteDuration =>
              logger.info(s"waiting on $receiveResultDeferred")
              receiveResultDeferred.get.timeout(requestTimeout).handleErrorWith {
                case t: TimeoutException =>
                  Concurrent[F].raiseError(RequestTimeout(requestTimeout))
                case t =>
                  Concurrent[F].raiseError(t)
              }
            case _ =>
              receiveResultDeferred.get
          }
        case None =>
          receiveResultDeferred.get
      }
      _ = logger.info(s"received result $receiveResult")
      message <- receiveResult.message
      // The consumer returns Either[Throwable, RESP] because the service has to be able to send an error through the channel; however,
      // the client API can just expose the more convenient F[RESP] and leave the error handling to F, which means we need 
      // to turn the Either[Throwable, RESP] result into F[RESP] before returning
      result = message match {
        case Right(payload) =>
          logger.info(s"client received success: ${receiveResult.toString.take(500)}")
          DefaultMessageReceiveResult(Monad[F].pure(payload.asInstanceOf[RESPONSE]), receiveResult.attributes, receiveResult.commit, receiveResult.rollback)
        case Left(t) =>
          logger.error(s"client received failure: ${receiveResult.toString.take(500)}", t)
          DefaultMessageReceiveResult(Concurrent[F].raiseError[RESPONSE](t), receiveResult.attributes, receiveResult.commit, receiveResult.rollback)
      }
    } yield DefaultRequestResponseResult(sendResult, result)
  }

  def stop =
    for {
      _ <- messageSubscription.cancel
      _ <- messageConsumer.stop
    } yield ()
}

case class CorrelationInfo[F[_], REQ, RESP](
  responseDeferred: Deferred[F, MessageReceiveResult[F, Either[Throwable, RESP]]],
  sendTime: Long,
  request: Option[REQ]
)

case class DefaultRequestResponseResult[F[_], REQUEST, RESPONSE](request: MessageSendResult[F, REQUEST], response: MessageReceiveResult[F, RESPONSE]) extends RequestResponseResult[F, REQUEST, RESPONSE]

object ConsumerProducerRequestResponseClient extends Logging {

  def apply[F[_] : Concurrent : Timer, REQ: Marshaller, RESP](
    messageConsumer: MessageConsumer[F, Either[Throwable, RESP]],
    messageProducer: () => MessageProducer[F, REQ],
    responseProducerId: Option[String],
    autoCommitResponse: Boolean = true,
    name: String = randomUUID.toString
  )(
    implicit responseMarshaller: Unmarshaller[Either[Throwable, RESP]]
    // monitoringPublisher: MonitoringPublisher
  ): F[ConsumerProducerRequestResponseClient[F, REQ, RESP]] =
    for {
      correlationsRef <- Ref.of[F, Map[String, CorrelationInfo[F, REQ, RESP]]](Map())
      subscription <- messageConsumer.subscribe { receiveResult =>
        logger.info(s"received response ${receiveResult.toString.take(500)}")

        receiveResult.correlationId match {
          case Some(correlationId) =>
            for {
              correlations <- correlationsRef.get
              _ <- correlations.get(correlationId) match {
                case Some(correlationInfo) =>
                  correlationInfo.responseDeferred.complete(receiveResult)
                case None =>
                  logger.error(s"$this received message that cannot be correlated to a request: ${receiveResult.toString.take(500)}")
                  Monad[F].unit
              }
              _ <- correlationsRef.update(_ - correlationId)
              _ <- if (autoCommitResponse) {
                val r = receiveResult.commit
                logger.info(s"client committed response ${receiveResult.toString.take(500)}")
                r
              } else Monad[F].unit
            } yield ()
          case None =>
            logger.error(s"${System.identityHashCode(this)} received message without correlation id: ${receiveResult.toString.take(500)}")
            Monad[F].unit
        }
      }
    } yield
      new ConsumerProducerRequestResponseClient(correlationsRef, subscription, messageConsumer, messageProducer, responseProducerId, autoCommitResponse, name)

}
