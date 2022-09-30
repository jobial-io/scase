package io.jobial.scase.core.impl

import cats.effect.Concurrent
import cats.effect.concurrent.Deferred
import cats.effect.concurrent.Ref
import cats.implicits._
import io.jobial.scase.core.MessageConsumer
import io.jobial.scase.core.MessageReceiveResult
import io.jobial.scase.core.MessageSubscription
import io.jobial.scase.core.ReceiveTimeout
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Unmarshaller
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

/**
 * Adds cancellation, subscription state. 
 */
abstract class DefaultMessageConsumer[F[_] : Concurrent, M] extends MessageConsumer[F, M] with CatsUtils with Logging {

  val receiveTimeoutInSubscribe = 1.second

  def receiveMessagesUntilCancelled[T](callback: MessageReceiveResult[F, M] => F[T], cancelled: Ref[F, Boolean], receiving: Deferred[F, Unit], receiveTimeout: FiniteDuration = 1.millis)(implicit u: Unmarshaller[M]): F[Unit] = {

    def continueIfNotCancelled =
      for {
        c <- cancelled.get
        r <- if (!c) receiveMessagesUntilCancelled(callback, cancelled, receiving, receiveTimeoutInSubscribe) else unit
      } yield r

    (for {
      result <- receive(Some(receiveTimeout)).handleErrorWith { t =>
        receiving.complete().attempt >>
          raiseError(t)
      }
      _ <- receiving.complete().attempt
      _ <- callback(result)
      _ <- continueIfNotCancelled
    } yield {
      logger.info(s"finished receiving messages in $this")
    }) handleErrorWith {
      case t: ReceiveTimeout =>
        continueIfNotCancelled
      case t =>
        error(s"stopped receiving messages on consumer $this", t)
    }
  }

  def initialize = unit

  override def subscribe[T](callback: MessageReceiveResult[F, M] => F[T])(implicit u: Unmarshaller[M]): F[MessageSubscription[F, M]] =
    for {
      _ <- initialize
      cancelled <- Ref.of[F, Boolean](false)
      cancellationHappened <- Deferred[F, Unit]
      receiving <- Deferred[F, Unit]
      _ <- Concurrent[F].start(receiveMessagesUntilCancelled(callback, cancelled, receiving))
      _ <- receiving.get
    } yield
      new MessageSubscription[F, M] {

        override def join =
          cancellationHappened.get

        override def cancel =
          cancelled.update(_ => true) >>
            cancellationHappened.complete()

        override def isCancelled =
          cancelled.get
      }
}
