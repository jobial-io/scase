package io.jobial.scase.core.impl

import cats.implicits._
import cats.effect.Concurrent
import cats.effect.concurrent.{Deferred, Ref}
import io.jobial.scase.core.{MessageConsumer, MessageReceiveResult, MessageSubscription}
import io.jobial.scase.marshalling.Unmarshaller

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
          for {
            _ <- cancelled.get
          } yield ()
        
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
