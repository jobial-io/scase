package io.jobial.scase.core

import cats.{Monad, MonadError}

import java.util.UUID.randomUUID
import cats.effect.{Concurrent, IO}
import cats.effect.concurrent.{Deferred, Ref}
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}
import io.jobial.scase.monitoring.{MonitoringPublisher, dummyPublisher}
import cats.implicits._
import scala.concurrent.ExecutionContext

case class CorrelationInfo[F[_], REQ, RESP](
  responseDeferred: Deferred[F, Either[Throwable, MessageReceiveResult[F, RESP]]],
  sendTime: Long,
  request: Option[REQ]
)

case class ConsumerProducerRequestResponseClient[F[_]: Concurrent, REQ: Marshaller, RESP](
  correlationsRef: Ref[F, Map[String, CorrelationInfo[F, REQ, RESP]]],
  messageSubscription: MessageSubscription[F, Either[Throwable, RESP]],
  messageConsumer: MessageConsumer[F, Either[Throwable, RESP]],
  messageProducer: () => MessageProducer[F, REQ],
  responseConsumerId: String,
  autoCommitResponse: Boolean,
  name: String
)(
  implicit   responseMarshallable: Unmarshaller[Either[Throwable, RESP]],
  monitoringPublisher: MonitoringPublisher
) extends RequestResponseClient[F, REQ, RESP] with Logging {

  val holdOntoOutstandingRequest = true

  //logger.debug(s"client expecting responses on ${messageConsumer}")

  def logOutsanding =
    for {
      correlations <- correlationsRef.get
    } yield
      if (correlations.size > 0)
        logger.warn(s"Outstanding correlations: ${correlations} on ${this}")

  //  def scheduleLogOutstanding: Future[_] =
  //    scheduledFuture(3 seconds) {
  //      logOutsanding
  //      scheduleLogOutstanding
  //    }
  //
  //  scheduleLogOutstanding

  case class ConsumerProducerRequestResult[RESPONSE <: RESP](response: F[MessageReceiveResult[F, RESPONSE]]) extends RequestResult[F, RESPONSE] {
    def commit = Monad[F].flatMap[MessageReceiveResult[F, RESPONSE], Unit](response)(x => x.commit())
  }

  def sendRequestWithResponseMapping[REQUEST <: REQ, RESPONSE <: RESP](request: REQUEST, requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE])
    (implicit sendRequestContext: SendRequestContext) =
    ConsumerProducerRequestResult {

      val producer = messageProducer()
      // TODO: this seems to be critical line for initialization, figure out why...
      //logger.debug(s"client sending requests to ${producer}")
      //logger.debug(s"client sending requests")
      //messageProducer.toString
      //println(sendRequestContext)
      val correlationId = randomUUID.toString

      monitoringPublisher.increment(request.getClass.getName)
      logger.info(s"sending ${request.toString.take(500)} with $correlationId using $this")

      //implicit val cs = IO.contextShift(ExecutionContext.global)

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
        sendResult <- producer.send(
          request,
          Map(
            CorrelationIdKey -> correlationId,
            ResponseConsumerIdKey -> responseConsumerId
          ) ++ sendRequestContext.requestTimeout.map(t => RequestTimeoutKey -> t.toMillis.toString)
        )
        _ = println("waiting for result...")
        receiveResult <- receiveResultDeferred.get
        // TODO: revisit this - maybe Marshallable[RESPONSE] should be an implicit
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

  def apply[F[_]: Concurrent, REQ: Marshaller, RESP](
    messageConsumer: MessageConsumer[F, Either[Throwable, RESP]],
    messageProducer: () => MessageProducer[F, REQ],
    responseConsumerId: String,
    autoCommitResponse: Boolean = true,
    name: String = randomUUID.toString
  )(
    implicit responseMarshallable: Unmarshaller[Either[Throwable, RESP]],
    monitoringPublisher: MonitoringPublisher = dummyPublisher
  ): F[ConsumerProducerRequestResponseClient[F, REQ, RESP]] =
    for {
      correlationsRef <- Ref.of[F, Map[String, CorrelationInfo[F, REQ, RESP]]](Map())
      subscription <- messageConsumer.subscribe { response =>
        println(s"received response ${response.toString.take(500)}")
        logger.debug(s"received response ${response.toString.take(500)}")

        response.correlationId match {
          case Some(correlationId) =>
            for {
              correlations <- correlationsRef.get
              _ <- correlations.get(correlationId) match {
                case Some(correlationInfo) =>
                  response.message match {
                    case Right(payload) =>
                      println(s"client received success: ${response.toString.take(500)}")
                      logger.debug(s"client received success: ${response.toString.take(500)}")
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

                    //                      for {
                    //                        request <- correlationInfo.request
                    //                      } yield
                    //                        monitoringPublisher.timing(request.getClass.getName, correlationInfo.sendTime)
                    case Left(t) =>
                      logger.error(s"client received failure: ${response.toString.take(500)}", t)
                      correlationInfo.responseDeferred.complete(Left(t))
                  }

                //                  
                case None =>
                  logger.error(s"${System.identityHashCode(this)} received message that cannot be correlated to a request: ${response.toString.take(500)}")
                  Monad[F].pure()
              }
              _ <- correlationsRef.update(_ - correlationId)
              _ <- if (autoCommitResponse) {
                val r = response.commit()
                logger.debug(s"client committed response ${response.toString.take(500)}")
                r
              } else Monad[F].pure()
            } yield ()
          case None =>
            logger.error(s"${System.identityHashCode(this)} received message without correlation id: ${response.toString.take(500)}")
            Monad[F].pure()
        }
      }
    } yield ConsumerProducerRequestResponseClient(correlationsRef, subscription, messageConsumer, messageProducer, responseConsumerId, autoCommitResponse, name)

}