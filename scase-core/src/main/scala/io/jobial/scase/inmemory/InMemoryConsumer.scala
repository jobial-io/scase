package io.jobial.scase.inmemory

import cats.Monad
import cats.effect.Concurrent
import cats.effect.Timer
import cats.effect.concurrent.Deferred
import cats.effect.concurrent.MVar
import cats.implicits._
import io.jobial.scase.core.MessageReceiveResult
import io.jobial.scase.core.ReceiveTimeout
import io.jobial.scase.core.impl.CatsUtils
import io.jobial.scase.core.impl.DefaultMessageConsumer
import io.jobial.scase.marshalling.Unmarshaller
import scala.concurrent.TimeoutException
import scala.concurrent.duration.FiniteDuration


class InMemoryConsumer[F[_] : Concurrent : Timer, M](
  val receiveResult: MVar[F, MessageReceiveResult[F, M]],
  producerDeferred: Deferred[F, InMemoryProducer[F, M]]
) extends DefaultMessageConsumer[F, M] {

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

  def apply[F[_] : Concurrent : Timer, M](producer: Option[InMemoryProducer[F, M]]): F[InMemoryConsumer[F, M]] = for {
    receiveResult <- MVar.empty[F, MessageReceiveResult[F, M]]
    producerDeferred <- Deferred[F, InMemoryProducer[F, M]]
    consumer = new InMemoryConsumer[F, M](receiveResult, producerDeferred)
    producer <- producer.map(Monad[F].pure).getOrElse(InMemoryProducer[F, M](List(consumer)))
    _ <- producer.consumers.update(l => consumer :: l.filter(_ != consumer))
    _ <- producerDeferred.complete(producer)
  } yield consumer
  
  def apply[F[_] : Concurrent : Timer, M]: F[InMemoryConsumer[F, M]] = apply(None)

  def apply[F[_] : Concurrent : Timer, M](producer: InMemoryProducer[F, M]): F[InMemoryConsumer[F, M]] = apply(Some(producer))
}