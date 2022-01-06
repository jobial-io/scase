package io.jobial.scase.core.impl

import cats.implicits._
import cats.effect.Concurrent
import cats.effect.concurrent.{Deferred, Ref}
import io.jobial.scase.core.{MessageConsumer, MessageReceiveResult, MessageSubscription}
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Unmarshaller

/**
 * Adds cancellation, subscription state. 
 */
trait DefaultMessageConsumer[F[_], M] extends MessageConsumer[F, M] with Logging {

  val subscriptions: Ref[F, List[MessageReceiveResult[F, M] => F[_]]]

  def receiveMessages[T](callback: MessageReceiveResult[F, M] => F[T], cancelled: Ref[F, Boolean])(implicit u: Unmarshaller[M], concurrent: Concurrent[F]): F[Unit]

  def receiveMessagesUntilCancelled[T](callback: MessageReceiveResult[F, M] => F[T], cancelled: Ref[F, Boolean])(implicit u: Unmarshaller[M], concurrent: Concurrent[F]): F[Unit] =
    for {
      _ <- receiveMessages(callback, cancelled)
      c <- cancelled.get
      _ <- if (!c) receiveMessagesUntilCancelled(callback, cancelled) else Concurrent[F].unit
    } yield {
      logger.info(s"finished receiving messages in $this")
    }

  def initialize(implicit concurrent: Concurrent[F]) = Concurrent[F].unit

  override def subscribe[T](callback: MessageReceiveResult[F, M] => F[T])(implicit u: Unmarshaller[M], concurrent: Concurrent[F]): F[MessageSubscription[F, M]] =
    for {
      _ <- initialize
      _ <- subscriptions.modify(s => (callback :: s, ()))
      cancelled <- Ref.of[F, Boolean](false)
      cancellationHappened <- Deferred[F, Unit]
      _ <- Concurrent[F].start(receiveMessagesUntilCancelled(callback, cancelled))
    } yield
      new MessageSubscription[F, M] {

        override def join =
          for {
            _ <- cancellationHappened.get
          } yield ()

        override def cancel =
          for {
            _ <- cancelled.update(_ => true)
            _ <- cancellationHappened.complete()
          } yield ()

        override def isCancelled = {
          // TODO: make this non-blocking
          cancelled.get
        }
      }

  // TODO: deliverToAllSubscribers, allowMultipleSubscribers
}
