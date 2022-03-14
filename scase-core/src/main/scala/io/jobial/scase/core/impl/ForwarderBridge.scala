package io.jobial.scase.core.impl

import cats.Monad
import cats.effect.Concurrent
import cats.effect.concurrent.Ref
import cats.implicits._
import io.jobial.scase.core.ReceiverClient
import io.jobial.scase.core._
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshaller
import io.jobial.scase.marshalling.Unmarshaller

class ForwarderBridge[F[_] : Concurrent, REQ: Unmarshaller, RESP: Marshaller](
  source: ReceiverClient[F, REQ],
  destination: MessageReceiveResult[F, RESP] => F[MessageSendResult[F, RESP]],
  filter: MessageReceiveResult[F, REQ] => F[Option[MessageReceiveResult[F, RESP]]],
  stopped: Ref[F, Boolean]
) extends Logging {

  def continueForwarding =
    for {
      stopped <- stopped.get
      r <- if (stopped) Monad[F].unit else forward
    } yield r

  def forward: F[Unit] =
    (for {
      receiveResult <- source.receiveWithContext
      filteredReceiveResult <- filter(receiveResult)
      sendResult <- filteredReceiveResult match {
        case Some(filteredReceiveResult) =>
          destination(filteredReceiveResult)
        case None =>
          Monad[F].unit
      }
      r <- continueForwarding
    } yield r) handleErrorWith {
      case t: Throwable =>
        logger.error(s"error while forwarding in $this", t)
        continueForwarding
    }

  def start = Concurrent[F].start(forward)

  def stop = stopped.set(true)
}

object ForwarderBridge {

  def apply[F[_] : Concurrent, M: Unmarshaller : Marshaller](
    source: ReceiverClient[F, M],
    destination: SenderClient[F, M]
  ) = for {
    stopped <- Ref.of[F, Boolean](false)
  } yield
    new ForwarderBridge[F, M, M](source, { r =>
      for {
        message <- r.message
        sendResult <- destination.send(message)(SendMessageContext(r.attributes))
      } yield sendResult
    }, { r =>
      Monad[F].pure(Some(DefaultMessageReceiveResult(r.message, r.attributes, Monad[F].unit, Monad[F].unit)))
    }, stopped)
}