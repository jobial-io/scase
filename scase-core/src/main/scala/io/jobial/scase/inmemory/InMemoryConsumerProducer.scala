package io.jobial.scase.inmemory

import cats.effect.Concurrent
import cats.{Monad, Traverse}
import cats.effect.concurrent.{Deferred, Ref}
import cats.implicits._
import cats.effect.implicits._
import io.jobial.scase.core.impl.DefaultMessageConsumer
import io.jobial.scase.core.{MessageConsumer, MessageProducer, MessageReceiveResult, MessageSendResult, MessageSubscription}
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}


trait InMemoryConsumerProducer[F[_], M] extends DefaultMessageConsumer[F, M] with MessageProducer[F, M] with Logging {

  val deliverToAllSubscribers: Boolean

  val allowMultipleSubscribers: Boolean

  def receiveMessages[T](callback: MessageReceiveResult[F, M] => F[T], cancelled: Deferred[F, Boolean])(implicit u: Unmarshaller[M], concurrent: Concurrent[F]) =
    // Noop as send handles the subscribers
    Concurrent[F].unit

  /**
   * TODO: currently this implementation propagates failures from the subscriptions to the sender mainly
   *  - to allow SNS topics to not commit failed deliveries. This behaviour should be reviewed. Also,
   *  - the subscribers here are not required to commit. This should also be reviewed.
   *
   * Warning: Marshaller (and the Unmarshaller in subscribe) is not used here, the message is delivered directly to the consumer.
   */
  def send(message: M, attributes: Map[String, String] = Map())(implicit m: Marshaller[M], concurrent: Concurrent[F]): F[MessageSendResult[M]] = {

    val messageReceiveResult = MessageReceiveResult(message, attributes, { () => Monad[F].unit }, { () => Monad[F].unit })

    for {
      r <- subscriptions.get
      //_ = println(r)
      _ <- Traverse[List].sequence[F, Any](for {
        subscription <- r.asInstanceOf[List[MessageReceiveResult[F, M] => F[Any]]]
      } yield {
        logger.debug(s"calling subscription on queue with $messageReceiveResult")
        subscription(messageReceiveResult)
      })
    } yield MessageSendResult[M]()


  }
}
