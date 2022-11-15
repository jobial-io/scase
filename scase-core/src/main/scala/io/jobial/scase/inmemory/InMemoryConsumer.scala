package io.jobial.scase.inmemory

import cats.Monad
import cats.effect.Deferred
import cats.effect.std.Queue
import cats.implicits._
import io.jobial.scase.core.MessageReceiveResult
import io.jobial.scase.core.ReceiveTimeout
import io.jobial.scase.core.impl.CatsUtils
import io.jobial.scase.core.impl.DefaultMessageConsumer
import io.jobial.scase.core.impl.TemporalEffect
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Unmarshaller
import scala.concurrent.TimeoutException
import scala.concurrent.duration.FiniteDuration


class InMemoryConsumer[F[_] : TemporalEffect, M](
  val receiveResult: Queue[F, MessageReceiveResult[F, M]],
  producerDeferred: Deferred[F, InMemoryProducer[F, M]]
) extends DefaultMessageConsumer[F, M] with CatsUtils with Logging {

  def receive(timeout: Option[FiniteDuration])(implicit u: Unmarshaller[M]) =
    take(receiveResult, timeout).handleErrorWith {
      case t: TimeoutException =>
        raiseError(ReceiveTimeout(timeout))
      case t =>
        raiseError(t)
    }

  def stop = unit

  def producer: F[InMemoryProducer[F, M]] = producerDeferred.get
}

object InMemoryConsumer extends CatsUtils {

  def apply[F[_] : TemporalEffect, M](producer: Option[InMemoryProducer[F, M]]): F[InMemoryConsumer[F, M]] = for {
    receiveResult <- Queue.bounded[F, MessageReceiveResult[F, M]](1)
    producerDeferred <- Deferred[F, InMemoryProducer[F, M]]
    consumer = new InMemoryConsumer[F, M](receiveResult, producerDeferred)
    producer <- producer.map(Monad[F].pure).getOrElse(InMemoryProducer[F, M](List(consumer)))
    _ <- producer.consumers.update(l => consumer :: l.filter(_ != consumer))
    _ <- producerDeferred.complete(producer)
  } yield consumer

  def apply[F[_] : TemporalEffect, M]: F[InMemoryConsumer[F, M]] = apply(None)

  def apply[F[_] : TemporalEffect, M](producer: InMemoryProducer[F, M]): F[InMemoryConsumer[F, M]] = apply(Some(producer))
}