package io.jobial.scase.inmemory

import cats.Monad
import cats.effect.concurrent.{Deferred, Ref, Semaphore}
import cats.effect.implicits.catsEffectSyntaxConcurrent
import cats.effect.{Concurrent, Timer}
import cats.implicits._
import io.jobial.scase.core.impl.DefaultMessageConsumer
import io.jobial.scase.core.{DefaultMessageReceiveResult, MessageProducer, MessageReceiveResult, MessageSendResult}
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}

import scala.concurrent.duration.FiniteDuration


class InMemoryConsumerProducer[F[_] : Concurrent : Timer, M](
  val messages: Ref[F, List[MessageReceiveResult[F, M]]],
  val receives: Ref[F, List[Deferred[F, MessageReceiveResult[F, M]]]],
  val receivedMessagesSemaphore: Semaphore[F]
) extends DefaultMessageConsumer[F, M] with MessageProducer[F, M] with Logging {

  def sendReceive: F[Unit] = {
    for {
      _ <- receivedMessagesSemaphore.acquire
      receive <- receives.modify(r => if (r.isEmpty) (Nil, None) else (r.tail, r.headOption))
      messageReceiveResult <- messages.modify(r => if (r.isEmpty) (Nil, None) else (r.tail, r.headOption))
      _ <- (receive, messageReceiveResult) match {
        case (Some(receive), Some(messageReceiveResult)) =>
          logger.info(s"completing send $receive on queue with $messageReceiveResult")
          for {
            _ <- receive.complete(messageReceiveResult)
            _ <- receivedMessagesSemaphore.release
            _ <- sendReceive
          } yield ()
        case _ =>
          for {
            _ <- messages.update(m => messageReceiveResult.toList ++ m)
            _ <- receives.update(r => receive.toList ++ r)
            _ <- receivedMessagesSemaphore.release
          } yield ()
      }
    } yield ()
  } handleErrorWith { t =>
    for {
      _ <- receivedMessagesSemaphore.release
      _ <- Concurrent[F].raiseError[Unit](t)
    } yield ()
  }

  /**
   * TODO: currently this implementation propagates failures from the subscriptions to the sender mainly
   *  - to allow SNS topics to not commit failed deliveries. This behaviour should be reviewed. Also,
   *  - the subscribers here are not required to commit. This should also be reviewed.
   */
  def send(message: M, attributes: Map[String, String] = Map())(implicit m: Marshaller[M]): F[MessageSendResult[F, M]] =
    for {
      _ <- messages.update(m => m :+ DefaultMessageReceiveResult[F, M](Monad[F].pure(message), attributes, Monad[F].unit, Monad[F].unit))
      _ <- sendReceive
    } yield new MessageSendResult[F, M] {
      def commit = Monad[F].unit

      def rollback = Monad[F].unit
    }

  def stop = Monad[F].unit

  def receive(timeout: Option[FiniteDuration])(implicit u: Unmarshaller[M]) =
    for {
      receive <- Deferred[F, MessageReceiveResult[F, M]]
      _ <- receives.update(receives => receives :+ receive)
      _ <- sendReceive
      _ = logger.info(s"waiting on receive $receive")
      result <- timeout.map(receive.get.timeout(_)).getOrElse(receive.get)
    } yield result
}

object InMemoryConsumerProducer {

  def apply[F[_] : Concurrent : Timer, M]: F[InMemoryConsumerProducer[F, M]] = for {
    messages <- Ref.of[F, List[MessageReceiveResult[F, M]]](List())
    receives <- Ref.of[F, List[Deferred[F, MessageReceiveResult[F, M]]]](List())
    receivedMessagesSemaphore <- Semaphore[F](1)
  } yield new InMemoryConsumerProducer[F, M](messages, receives, receivedMessagesSemaphore)
}