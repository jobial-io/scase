package io.jobial.scase.core.impl

import cats.Monad
import cats.effect.Concurrent
import cats.implicits._
import io.jobial.scase.core.SendMessageContext
import io.jobial.scase.core.{CorrelationIdKey, MessageProducer, MessageSendResult, RequestTimeoutKey, ResponseProducerIdKey, SendRequestContext, SenderClient}
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshaller
import java.util.UUID.randomUUID

// TODO: add autocommit
class ProducerSenderClient[F[_] : Concurrent, REQ: Marshaller](
  messageProducer: MessageProducer[F, REQ],
  responseProducerId: Option[String]
) extends SenderClient[F, REQ] with Logging {

  override def send[REQUEST <: REQ](request: REQUEST)(implicit sendMessageContext: SendMessageContext): F[MessageSendResult[F, REQUEST]] = {
    val correlationId = randomUUID.toString

    for {
      _ <- info[F](s"sending request with correlation id $correlationId on $messageProducer")
      sendResult <- messageProducer.send(
        request,
        Map(
          CorrelationIdKey -> correlationId
        ) ++ responseProducerId.map(ResponseProducerIdKey -> _)
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
    responseProducerId: Option[String] = None
  ) = Concurrent[F].delay(new ProducerSenderClient(messageProducer, responseProducerId))
}