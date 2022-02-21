package io.jobial.scase.inmemory

import cats.effect.Concurrent
import cats.effect.concurrent.{Deferred, Ref}
import cats.implicits._
import cats.{Monad, Traverse}
import io.jobial.scase.core.impl.DefaultMessageConsumer
import io.jobial.scase.core.{DefaultMessageReceiveResult, MessageProducer, MessageReceiveResult, MessageSendResult}
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}

import scala.concurrent.duration.FiniteDuration


class InMemoryConsumerProducer[F[_] : Concurrent, M](
  val receives: Ref[F, List[Deferred[F, MessageReceiveResult[F, M]]]]
) extends DefaultMessageConsumer[F, M] with MessageProducer[F, M] with Logging {

  /**
   * TODO: currently this implementation propagates failures from the subscriptions to the sender mainly
   *  - to allow SNS topics to not commit failed deliveries. This behaviour should be reviewed. Also,
   *  - the subscribers here are not required to commit. This should also be reviewed.
   */
  def send(message: M, attributes: Map[String, String] = Map())(implicit m: Marshaller[M]): F[MessageSendResult[F, M]] =
    for {
      r <- receives.modify(r => (List(), r))
      messageReceiveResult = DefaultMessageReceiveResult[F, M](Monad[F].pure(message), attributes, Monad[F].unit, Monad[F].unit)
      _ <- Traverse[List].sequence[F, Unit](for {
        receive <- r
      } yield {
        logger.info(s"completing receive $receive on queue with $messageReceiveResult")
        receive.complete(messageReceiveResult)
      })
    } yield new MessageSendResult[F, M] {
      def commit = Monad[F].unit

      def rollback = Monad[F].unit
    }

  def stop = Monad[F].unit

  def receive(timeout: Option[FiniteDuration])(implicit u: Unmarshaller[M]) =
    for {
      receive <- Deferred[F, MessageReceiveResult[F, M]]
      _ <- receives.update(receives => receive :: receives)
      _ = logger.info(s"waiting on receive $receive")
      result <- receive.get
    } yield result
}

object InMemoryConsumerProducer {

  def apply[F[_] : Concurrent, M]: F[InMemoryConsumerProducer[F, M]] = for {
    receives <- Ref.of[F, List[Deferred[F, MessageReceiveResult[F, M]]]](List())
  } yield new InMemoryConsumerProducer[F, M](receives)
}