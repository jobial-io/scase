package io.jobial.scase.core

import cats.effect.Concurrent
import cats.effect.concurrent.{Deferred, Ref}
import cats.implicits._
import io.jobial.scase.marshalling.Unmarshaller

import scala.concurrent.duration._

case class MessageReceiveResult[F[_], M](
  message: M,
  attributes: Map[String, String],
  commit: () => F[Unit], // commit the message
  rollback: () => F[_]
) {

  def correlationId = attributes.get(CorrelationIdKey)

  def requestTimeout = attributes.get(RequestTimeoutKey).map(_.toLong.millis)

  def responseConsumerId = attributes.get(ResponseConsumerIdKey)
}

trait MessageSubscription[F[_], M] {

  def join: F[_]

  def cancel: F[_] // cancel the subscription

  def isCancelled: F[Boolean]
}

trait MessageConsumer[F[_], M] {

  // Subscribes to the message source. In the background, subscribe might start async processing (e.g. a Fiber to poll messages in a source).
  def subscribe[T](callback: MessageReceiveResult[F, M] => F[T])(implicit u: Unmarshaller[M], concurrent: Concurrent[F]): F[MessageSubscription[F, M]]
}

/**
 * Adds cancellation, subscription state. 
 */
trait DefaultMessageConsumer[F[_], M] extends MessageConsumer[F, M] {

  val subscriptions: Ref[F, List[MessageReceiveResult[F, M] => F[_]]]

  def receiveMessages[T](callback: MessageReceiveResult[F, M] => F[T])(implicit u: Unmarshaller[M], concurrent: Concurrent[F]): F[Unit]

  override def subscribe[T](callback: MessageReceiveResult[F, M] => F[T])(implicit u: Unmarshaller[M], concurrent: Concurrent[F]): F[MessageSubscription[F, M]] =
    for {
      _ <- subscriptions.update(callback :: _)
      cancelled <- Deferred[F, Boolean]
      _ <- Concurrent[F].start(receiveMessages(callback))
    } yield
      new MessageSubscription[F, M] {

        override def join =
          cancelled.get

        override def cancel =
          cancelled.complete(true)

        override def isCancelled = {
          // TODO: make this non-blocking
          cancelled.get
        }
      }

  //        if (deliverToAllSubscribers)
  //        else {
  //          val size = subscriptions.size
  //          if (size > 0) {
  //            val subscription = subscriptions.keys.drop(Random.nextInt(size)).headOption
  //            subscription.orElse(subscriptions.keys.lastOption).toSeq
  //          } else Seq()
  //        }


  //    if (subscriptions.size > 0 && !allowMultipleSubscribers)
  //      throw new IllegalStateException("Trying to subscribe multiple times")
  //
  //    subscriptions.put(callback, callback)
  //
  //    implicit val cs = IO.contextShift(ExecutionContext.global)

}

case class CouldNotFindMessageToCommit[M](
  message: M
) extends IllegalStateException