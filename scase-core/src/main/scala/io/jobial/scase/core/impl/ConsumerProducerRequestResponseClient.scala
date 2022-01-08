package io.jobial.scase.core.impl

import cats.effect.concurrent.{Deferred, Ref}
import cats.effect.implicits.catsEffectSyntaxConcurrent
import cats.effect.{Concurrent, Timer}
import cats.implicits._
import cats.{Monad, MonadError}
import io.jobial.scase.core.{CorrelationIdKey, MessageConsumer, MessageProducer, MessageReceiveResult, MessageSubscription, RequestResponseClient, RequestResponseMapping, RequestResult, RequestTimeoutKey, ResponseProducerIdKey, SendRequestContext}
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}

import java.util.UUID.randomUUID
import scala.concurrent.duration.FiniteDuration

case class CorrelationInfo[F[_], REQ, RESP](
  responseDeferred: Deferred[F, Either[Throwable, MessageReceiveResult[F, RESP]]],
  sendTime: Long,
  request: Option[REQ]
)

class ConsumerProducerRequestResponseClient[F[_] : Concurrent : Timer, REQ: Marshaller, RESP](
  correlationsRef: Ref[F, Map[String, CorrelationInfo[F, REQ, RESP]]],
  messageSubscription: MessageSubscription[F, Either[Throwable, RESP]],
  messageConsumer: MessageConsumer[F, Either[Throwable, RESP]],
  messageProducer: () => MessageProducer[F, REQ],
  responseProducerId: String,
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

  case class ConsumerProducerRequestResult[RESPONSE <: RESP](response: F[MessageReceiveResult[F, RESPONSE]]) extends RequestResult[F, RESPONSE] {
    def commit = Monad[F].flatMap[MessageReceiveResult[F, RESPONSE], Unit](response)(x => x.commit())
  }

  def sendRequestWithResponseMapping[REQUEST <: REQ, RESPONSE <: RESP](request: REQUEST, requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE])
    (implicit sendRequestContext: SendRequestContext) =
    ConsumerProducerRequestResult {

      val producer = messageProducer()
      val correlationId = randomUUID.toString

      //monitoringPublisher.increment(request.getClass.getName)
      logger.info(s"sending ${request.toString.take(500)} with $correlationId using $this")

      for {
        receiveResultDeferred <- Deferred[F, Either[Throwable, MessageReceiveResult[F, RESP]]]
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
            CorrelationIdKey -> correlationId,
            ResponseProducerIdKey -> responseProducerId
          ) ++ sendRequestContext.requestTimeout.map(t => RequestTimeoutKey -> t.toMillis.toString)
        )
        receiveResult <- sendRequestContext.requestTimeout match {
          case Some(requestTimeout) =>
            requestTimeout match {
              case requestTimeout: FiniteDuration =>
                receiveResultDeferred.get.timeout(requestTimeout)
              case _ =>
                receiveResultDeferred.get
            }
          case None =>
            receiveResultDeferred.get
        }
        r <- receiveResult.asInstanceOf[Either[Throwable, MessageReceiveResult[F, RESPONSE]]] match {
          case Right(r) =>
            Monad[F].pure(r)
          case Left(t) =>
            MonadError[F, Throwable].raiseError(t)
        }
      } yield r
    }

}

object ConsumerProducerRequestResponseClient extends Logging {

  def apply[F[_] : Concurrent : Timer, REQ: Marshaller, RESP](
    messageConsumer: MessageConsumer[F, Either[Throwable, RESP]],
    messageProducer: () => MessageProducer[F, REQ],
    responseProducerId: String,
    autoCommitResponse: Boolean = true,
    name: String = randomUUID.toString
  )(
    implicit responseMarshaller: Unmarshaller[Either[Throwable, RESP]]
    // monitoringPublisher: MonitoringPublisher
  ): F[ConsumerProducerRequestResponseClient[F, REQ, RESP]] =
    for {
      correlationsRef <- Ref.of[F, Map[String, CorrelationInfo[F, REQ, RESP]]](Map())
      subscription <- messageConsumer.subscribe { response =>
        logger.info(s"received response ${response.toString.take(500)}")

        response.correlationId match {
          case Some(correlationId) =>
            for {
              correlations <- correlationsRef.get
              _ <- correlations.get(correlationId) match {
                case Some(correlationInfo) =>
                  response.message match {
                    case Right(payload) =>
                      logger.info(s"client received success: ${response.toString.take(500)}")
                      correlationInfo.responseDeferred.complete(
                        Right(
                          MessageReceiveResult(
                            payload,
                            response.attributes,
                            {
                              response.commit
                              // TODO: add removal
                            },
                            {
                              response.rollback
                              // TODO: add removal
                            }
                          )
                        )
                      )

                    case Left(t) =>
                      logger.error(s"client received failure: ${response.toString.take(500)}", t)
                      correlationInfo.responseDeferred.complete(Left(t))
                  }

                case None =>
                  logger.error(s"${System.identityHashCode(this)} received message that cannot be correlated to a request: ${response.toString.take(500)}")
                  Monad[F].unit
              }
              _ <- correlationsRef.update(_ - correlationId)
              _ <- if (autoCommitResponse) {
                val r = response.commit()
                logger.info(s"client committed response ${response.toString.take(500)}")
                r
              } else Monad[F].unit
            } yield ()
          case None =>
            logger.error(s"${System.identityHashCode(this)} received message without correlation id: ${response.toString.take(500)}")
            Monad[F].unit
        }
      }
    } yield new ConsumerProducerRequestResponseClient(correlationsRef, subscription, messageConsumer, messageProducer, responseProducerId, autoCommitResponse, name)

}