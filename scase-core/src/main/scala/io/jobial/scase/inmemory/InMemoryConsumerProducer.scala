package io.jobial.scase.inmemory

import cats.effect.Concurrent
import cats.effect.Timer
import cats.effect.concurrent.Deferred
import cats.effect.concurrent.Ref
import cats.effect.concurrent.Semaphore
import cats.effect.implicits.catsEffectSyntaxConcurrent
import cats.implicits._
import io.jobial.scase.core.DefaultMessageReceiveResult
import io.jobial.scase.core.MessageProducer
import io.jobial.scase.core.MessageReceiveResult
import io.jobial.scase.core.MessageSendResult
import io.jobial.scase.core.ReceiveTimeout
import io.jobial.scase.core.impl.CatsUtils
import io.jobial.scase.core.impl.DefaultMessageConsumer
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshaller
import io.jobial.scase.marshalling.Unmarshaller
import scala.concurrent.TimeoutException
import scala.concurrent.duration.FiniteDuration


class InMemoryConsumerProducer[F[_] : Concurrent : Timer, M](
  val messages: Ref[F, List[MessageReceiveResult[F, M]]],
  val receives: Ref[F, List[Deferred[F, MessageReceiveResult[F, M]]]],
  val receivedMessagesSemaphore: Semaphore[F]
) extends DefaultMessageConsumer[F, M] with MessageProducer[F, M] with CatsUtils with Logging {

  protected def sendReceive: F[Unit] = {
    for {
      _ <- receivedMessagesSemaphore.acquire
      receiveList <- receives.modify(r => (Nil, r))
      messageReceiveResult <- messages.modify(r => if (r.isEmpty) (Nil, None) else (r.tail, r.headOption))
      _ <- (receiveList, messageReceiveResult) match {
        case (_ :: _, Some(messageReceiveResult)) =>
          debug(s"completing send $receiveList on queue with $messageReceiveResult") >>
            receiveList.map(_.complete(messageReceiveResult)).sequence >>
            receivedMessagesSemaphore.release >>
            sendReceive
        case _ =>
            messages.update(m => messageReceiveResult.toList ++ m) >>
            receives.update(r => receiveList) >>
            receivedMessagesSemaphore.release
      }
    } yield ()
  } handleErrorWith { t =>
    receivedMessagesSemaphore.release >>
      raiseError(t)
  }

  /**
   * TODO: currently this implementation propagates failures from the subscriptions to the sender mainly
   *  - to allow SNS topics to not commit failed deliveries. This behaviour should be reviewed. Also,
   *  - the subscribers here are not required to commit. This should also be reviewed.
   */
  def send(message: M, attributes: Map[String, String] = Map())(implicit m: Marshaller[M]): F[MessageSendResult[F, M]] =
    for {
      _ <- messages.update(m => m :+
        DefaultMessageReceiveResult[F, M](pure(message), attributes, unit, unit,
          raiseError(new IllegalStateException("No underlying message")),
          raiseError(new IllegalStateException("No underlying context"))))
      _ <- sendReceive
      _ <- debug(s"sent message $message")
    } yield new MessageSendResult[F, M] {
      def commit = unit

      def rollback = unit
    }

  def receive(timeout: Option[FiniteDuration])(implicit u: Unmarshaller[M]) =
    for {
      receive <- Deferred[F, MessageReceiveResult[F, M]]
      _ <- receives.update(receives => receive :: receives)
      _ <- sendReceive
      result <- timeout.map(receive.get.timeout(_).handleErrorWith {
        case t: TimeoutException =>
          raiseError(ReceiveTimeout(timeout))
        case t =>
          raiseError(t)
      }).getOrElse(receive.get)
    } yield result

  def stop = unit

}

object InMemoryConsumerProducer {

  def apply[F[_] : Concurrent : Timer, M]: F[InMemoryConsumerProducer[F, M]] = for {
    messages <- Ref.of[F, List[MessageReceiveResult[F, M]]](List())
    receives <- Ref.of[F, List[Deferred[F, MessageReceiveResult[F, M]]]](List())
    receivedMessagesSemaphore <- Semaphore[F](1)
  } yield new InMemoryConsumerProducer[F, M](messages, receives, receivedMessagesSemaphore)
}