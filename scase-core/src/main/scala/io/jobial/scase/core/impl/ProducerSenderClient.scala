package io.jobial.scase.core.impl

import cats.Monad
import cats.effect.Concurrent
import cats.implicits._
import io.jobial.scase.core.{CorrelationIdKey, MessageProducer, MessageSendResult, RequestTimeoutKey, ResponseProducerIdKey, SendRequestContext, SenderClient}
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshaller

import java.util.UUID.randomUUID

// TODO: add autocommit
class ProducerSenderClient[F[_] : Concurrent, REQ: Marshaller](
  messageProducer: MessageProducer[F, REQ],
  responseProducerId: String
) extends SenderClient[F, REQ] with Logging {

  override def send[REQUEST <: REQ](request: REQUEST)(implicit sendRequestContext: SendRequestContext): F[MessageSendResult[F, REQUEST]] = {
    val correlationId = randomUUID.toString
    logger.info(s"sending request with correlation id $correlationId")

    for {
      sendResult <- messageProducer.send(
        request,
        Map(
          CorrelationIdKey -> correlationId,
          ResponseProducerIdKey -> responseProducerId
        ) ++ sendRequestContext.requestTimeout.map(t => RequestTimeoutKey -> t.toMillis.toString)
      )
    } yield sendResult.asInstanceOf[MessageSendResult[F, REQUEST]]
  }

  def stop = messageProducer.stop
}

case class DefaultMessageSendResult[F[_] : Monad, M](commit: F[Unit], rollback: F[Unit])
  extends MessageSendResult[F, M]

object ProducerSenderClient {

  def apply[F[_] : Concurrent, REQ: Marshaller](
    messageProducer: MessageProducer[F, REQ],
    responseProducerId: String = randomUUID.toString
  ) = Concurrent[F].delay(new ProducerSenderClient(messageProducer, responseProducerId))
}