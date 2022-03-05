package io.jobial.scase.core.impl

import cats.effect.Concurrent
import cats.implicits._
import io.jobial.scase.core.{MessageConsumer, ReceiverClient}
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Unmarshaller

import scala.concurrent.duration.FiniteDuration

class ConsumerReceiverClient[F[_] : Concurrent, M: Unmarshaller](
  messageConsumer: MessageConsumer[F, M]
) extends ReceiverClient[F, M] with Logging {

  def receive: F[M] =
    for {
      result <- receiveWithContext
      message <- result.message
    } yield message

  def receive(timeout: FiniteDuration) =
    for {
      result <- receiveWithContext(timeout)
      message <- result.message
    } yield message

  def receiveWithContext =
    messageConsumer.receive(None)

  def receiveWithContext(timeout: FiniteDuration) =
    messageConsumer.receive(Some(timeout))

  def stop = messageConsumer.stop
}

object ConsumerReceiverClient {

  def apply[F[_] : Concurrent, M: Unmarshaller](
    messageConsumer: MessageConsumer[F, M]
  ) = Concurrent[F].delay(new ConsumerReceiverClient(messageConsumer))
}