package io.jobial.scase.core.impl

import cats.effect.Deferred
import cats.effect.Ref
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
abstract class DefaultMessageConsumer[F[_] : ConcurrentEffect, M] extends MessageConsumer[F, M] with CatsUtils with Logging {

  val receiveTimeoutInSubscribe = 1.second

  def receiveMessagesUntilCancelled[T](callback: MessageReceiveResult[F, M] => F[T], cancelled: Ref[F, Boolean], receiving: Deferred[F, Unit], receiveTimeout: FiniteDuration = receiveTimeoutInSubscribe, receivingCompleted: Boolean = false)(implicit u: Unmarshaller[M]): F[Unit] = {

    def continueIfNotCancelled =
      for {
        c <- cancelled.get
        r <- if (!c)
          receiveMessagesUntilCancelled(callback, cancelled, receiving, receiveTimeoutInSubscribe, true)
        else
          trace(s"subscription stopped receiving messages in consumer $this")
      } yield r

    (for {
      result <- guarantee(receive(if (receivingCompleted) Some(receiveTimeout) else Some(1.milli))) {
        whenA(!receivingCompleted) {
          receiving.complete().attempt
        }
      }
      _ <- start(continueIfNotCancelled)
      r <- callback(result)
    } yield ()) handleErrorWith {
      case t: ReceiveTimeout =>
        continueIfNotCancelled
      case t =>
        error(s"subscription stopped receiving messages in consumer $this", t)
    }
  }

  def receive(timeout: Option[FiniteDuration])(implicit u: Unmarshaller[M]): F[MessageReceiveResult[F, M]]

  def initialize = unit

  override def subscribe[T](callback: MessageReceiveResult[F, M] => F[T])(implicit u: Unmarshaller[M]): F[MessageSubscription[F, M]] =
    for {
      _ <- initialize
      cancelled <- Ref.of[F, Boolean](false)
      cancellationHappened <- Deferred[F, Unit]
      receiving <- Deferred[F, Unit]
      subscription = new MessageSubscription[F, M] {

        override def join =
          cancellationHappened.get

        override def cancel =
          cancelled.update(_ => true) >>
            cancellationHappened.complete() >>
            unit

        override def isCancelled =
          cancelled.get
      }
      _ <- start(receiveMessagesUntilCancelled(callback, cancelled, receiving))
      _ <- receiving.get
      _ <- trace(s"new subscription $subscription")
    } yield subscription
}
