package io.jobial.scase.inmemory

import cats.effect.{Concurrent, Sync}
import cats.{Monad, Traverse}
import cats.effect.concurrent.{Deferred, Ref}
import cats.implicits._
import cats.effect.implicits._
import io.jobial.scase.core.impl.DefaultMessageConsumer
import io.jobial.scase.core.{DefaultMessageReceiveResult, MessageConsumer, MessageProducer, MessageReceiveResult, MessageSendResult, MessageSubscription}
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}


class InMemoryConsumerProducer[F[_] : Concurrent, M](
  val subscriptions: Ref[F, List[MessageReceiveResult[F, M] => F[_]]],
  deliverToAllSubscribers: Boolean,
  allowMultipleSubscribers: Boolean
) extends DefaultMessageConsumer[F, M] with MessageProducer[F, M] with Logging {

  def receiveMessages[T](callback: MessageReceiveResult[F, M] => F[T], cancelled: Ref[F, Boolean])(implicit u: Unmarshaller[M]) =
  // Noop as send handles the subscribers
    Concurrent[F].unit

  override def receiveMessagesUntilCancelled[T](callback: MessageReceiveResult[F, M] => F[T], cancelled: Ref[F, Boolean])(implicit u: Unmarshaller[M]) =
  // Noop as send handles the subscribers
    Concurrent[F].unit

  /**
   * TODO: currently this implementation propagates failures from the subscriptions to the sender mainly
   *  - to allow SNS topics to not commit failed deliveries. This behaviour should be reviewed. Also,
   *  - the subscribers here are not required to commit. This should also be reviewed.
   *
   * Warning: Marshaller (and the Unmarshaller in subscribe) is not used here, the message is delivered directly to the consumer.
   */
  def send(message: M, attributes: Map[String, String] = Map())(implicit m: Marshaller[M]): F[MessageSendResult[F, M]] = {

    val messageReceiveResult = DefaultMessageReceiveResult[F, M](Monad[F].pure(message), attributes, Monad[F].unit, Monad[F].unit)

    for {
      r <- subscriptions.get
      _ <- Traverse[List].sequence[F, Any](for {
        subscription <- r.asInstanceOf[List[MessageReceiveResult[F, M] => F[Any]]]
      } yield {
        logger.info(s"calling subscription on queue with $messageReceiveResult")
        subscription(messageReceiveResult)
      })
    } yield new MessageSendResult[F, M] {
      def commit = Monad[F].unit

      def rollback = Monad[F].unit
    }


  }
}

object InMemoryConsumerProducer {

  def apply[F[_] : Concurrent, M](
    deliverToAllSubscribers: Boolean = true,
    allowMultipleSubscribers: Boolean = true
  ): F[InMemoryConsumerProducer[F, M]] = for {
    subscriptions <- Ref.of[F, List[MessageReceiveResult[F, M] => F[_]]](List())
  } yield new InMemoryConsumerProducer[F, M](subscriptions, deliverToAllSubscribers, allowMultipleSubscribers)
}