package io.jobial.scase.core.impl

import cats.effect.Concurrent
import cats.effect.concurrent.{Deferred, Ref}
import cats.implicits._
import io.jobial.scase.core.{MessageConsumer, MessageReceiveResult, MessageSubscription, ReceiveTimeout}
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Unmarshaller

import scala.concurrent.duration.DurationInt

/**
 * Adds cancellation, subscription state. 
 */
abstract class DefaultMessageConsumer[F[_] : Concurrent, M] extends MessageConsumer[F, M] with Logging {

  def receiveMessagesUntilCancelled[T](callback: MessageReceiveResult[F, M] => F[T], cancelled: Ref[F, Boolean])(implicit u: Unmarshaller[M]): F[Unit] = {

    def continueIfNotCancelled =
      for {
        c <- cancelled.get
        r <- if (!c) receiveMessagesUntilCancelled(callback, cancelled) else Concurrent[F].unit
      } yield r

    (for {
      result <- receive(Some(1.second))
      _ <- callback(result)
      _ <- continueIfNotCancelled
    } yield {
      logger.info(s"finished receiving messages in $this")
    }) handleErrorWith {
      case t: ReceiveTimeout[F] =>
        continueIfNotCancelled
      case t =>
        error[F](s"stopped receiving messages on consumer $this", t)
    }
  }

  def initialize = Concurrent[F].unit

  override def subscribe[T](callback: MessageReceiveResult[F, M] => F[T])(implicit u: Unmarshaller[M]): F[MessageSubscription[F, M]] =
    for {
      _ <- initialize
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
}
