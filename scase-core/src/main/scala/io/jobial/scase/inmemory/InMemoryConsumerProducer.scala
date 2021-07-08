package io.jobial.scase.inmemory

import cats.effect.Concurrent
import cats.{Monad, Traverse}
import cats.effect.concurrent.{Deferred, Ref}
import cats.implicits._
import cats.effect.implicits._
import io.jobial.scase.core.{MessageConsumer, MessageProducer, MessageReceiveResult, MessageSendResult, MessageSubscription}
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}


trait InMemoryConsumerProducer[F[_], M] extends MessageConsumer[F, M] with MessageProducer[F, M] {

  val deliverToAllSubscribers: Boolean

  val allowMultipleSubscribers: Boolean

  def subscriptions: Ref[F, List[MessageReceiveResult[F, M] => F[_]]]

  /**
   * TODO: currently this implementation propagates failures from the subscriptions to the sender mainly
   *  - to allow SNS topics to not commit failed deliveries. This behaviour should be reviewed. Also,
   *  - the subscribers here are not required to commit. This should also be reviewed.
   *
   * Warning: Marshaller (and the Unmarshaller in subscribe) is not used here, the message is delivered directly to the consumer.
   */
  def send(message: M, attributes: Map[String, String] = Map())(implicit m: Marshaller[M], mf: Monad[F]): F[MessageSendResult[M]] = {

    val messageReceiveResult = MessageReceiveResult(message, attributes, { () => Monad[F].pure() }, { () => Monad[F].pure() })

    for {
      r <- subscriptions.get
      _ = println(r)
      _ <- Traverse[List].sequence[F, Any](for {
        subscription <- r.asInstanceOf[List[MessageReceiveResult[F, M] => F[Any]]]
      } yield {
        println(s"calling subscription on queue with $messageReceiveResult")
        subscription(messageReceiveResult)
      })
    } yield MessageSendResult[M]()

    //        if (deliverToAllSubscribers)
    //        else {
    //          val size = subscriptions.size
    //          if (size > 0) {
    //            val subscription = subscriptions.keys.drop(Random.nextInt(size)).headOption
    //            subscription.orElse(subscriptions.keys.lastOption).toSeq
    //          } else Seq()
    //        }
  }

  /**
   *
   * Warning: the Unmarshaller is not used here, the message is delivered directly to the consumer.
   *
   * @param callback
   * @param u
   * @tparam T
   * @return
   */
  override def subscribe[T](callback: MessageReceiveResult[F, M] => F[T])(implicit u: Unmarshaller[M], c: Concurrent[F]) = {
    for {
      _ <- subscriptions.update(callback :: _)
      _ = println(callback)
      cancelled <- Deferred[F, Boolean]
    } yield new MessageSubscription[F, M] {

      override def join =
        cancelled.get

      override def cancel =
        cancelled.complete(true)

      override def isCancelled = {
        // TODO: make this non-blocking
        cancelled.get
      }
    }



    //    if (subscriptions.size > 0 && !allowMultipleSubscribers)
    //      throw new IllegalStateException("Trying to subscribe multiple times")
    //
    //    subscriptions.put(callback, callback)
    //
    //    implicit val cs = IO.contextShift(ExecutionContext.global)

  }
}
