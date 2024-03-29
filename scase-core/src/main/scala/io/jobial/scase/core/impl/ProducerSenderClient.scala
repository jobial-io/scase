package io.jobial.scase.core.impl

import cats.Monad
import cats.implicits._
import io.jobial.scase.core.CorrelationIdKey
import io.jobial.scase.core.MessageProducer
import io.jobial.scase.core.MessageSendResult
import io.jobial.scase.core.ResponseProducerIdKey
import io.jobial.scase.core.ResponseTopicKey
import io.jobial.scase.core.SendMessageContext
import io.jobial.scase.core.SenderClient
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshaller
import java.util.UUID.randomUUID

// TODO: add autocommit
class ProducerSenderClient[F[_] : ConcurrentEffect, REQ: Marshaller](
  messageProducer: MessageProducer[F, REQ],
  responseProducerId: Option[String]
) extends SenderClient[F, REQ] with Logging {

  override def send[REQUEST <: REQ](request: REQUEST)(implicit sendMessageContext: SendMessageContext): F[MessageSendResult[F, REQUEST]] = {
    val correlationId = randomUUID.toString

    for {
      _ <- trace(s"sending message with correlation id $correlationId on $messageProducer")
      sendResult <- messageProducer.send(
        request,
        Map(
          CorrelationIdKey -> correlationId
        ) ++ responseProducerId.map(ResponseProducerIdKey -> _)
          ++ responseProducerId.map(ResponseTopicKey -> _)
          ++ sendMessageContext.attributes
      )
    } yield sendResult.asInstanceOf[MessageSendResult[F, REQUEST]]
  }

  def stop = messageProducer.stop
}

case class DefaultMessageSendResult[F[_] : Monad, M](commit: F[Unit], rollback: F[Unit])
  extends MessageSendResult[F, M]

object ProducerSenderClient extends CatsUtils {

  def apply[F[_] : ConcurrentEffect, REQ: Marshaller](
    messageProducer: MessageProducer[F, REQ],
    responseProducerId: Option[String] = None
  ) = delay(new ProducerSenderClient(messageProducer, responseProducerId))
}