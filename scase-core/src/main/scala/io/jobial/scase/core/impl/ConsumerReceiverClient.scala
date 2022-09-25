package io.jobial.scase.core.impl

import cats.effect.Concurrent
import cats.implicits._
import io.jobial.scase.core.MessageConsumer
import io.jobial.scase.core.ReceiverClient
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Unmarshaller
import scala.concurrent.duration.FiniteDuration

class ConsumerReceiverClient[F[_] : Concurrent, M: Unmarshaller](
  messageConsumer: MessageConsumer[F, M],
  autoCommit: Boolean = true
) extends ReceiverClient[F, M] with CatsUtils with Logging {

  def receive: F[M] =
    for {
      result <- receiveWithContext
      message <- result.message
    } yield message

  def receive(timeout: FiniteDuration) =
    for {
      result <- receiveWithContext(timeout)
      _ <- whenA(autoCommit)(result.commit)
      message <- result.message
    } yield message

  def receiveWithContext =
    messageConsumer.receive(None)

  def receiveWithContext(timeout: FiniteDuration) =
    messageConsumer.receive(Some(timeout))

  def stop = messageConsumer.stop
}

object ConsumerReceiverClient extends CatsUtils {

  def apply[F[_] : Concurrent, M: Unmarshaller](
    messageConsumer: MessageConsumer[F, M]
  ) = delay(new ConsumerReceiverClient(messageConsumer))
}