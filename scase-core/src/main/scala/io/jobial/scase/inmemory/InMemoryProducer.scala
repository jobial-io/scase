package io.jobial.scase.inmemory

import cats.effect.Concurrent
import cats.effect.Timer
import cats.effect.concurrent.Ref
import cats.implicits._
import io.jobial.scase.core.DefaultMessageReceiveResult
import io.jobial.scase.core.MessageProducer
import io.jobial.scase.core.MessageSendResult
import io.jobial.scase.core.impl.CatsUtils
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshaller


class InMemoryProducer[F[_] : Concurrent : Timer, M](
  val consumers: Ref[F, List[InMemoryConsumer[F, M]]]
) extends MessageProducer[F, M] with CatsUtils with Logging {

  def send(message: M, attributes: Map[String, String] = Map())(implicit m: Marshaller[M]): F[MessageSendResult[F, M]] =
    for {
      consumers <- consumers.get
      _ <- {
        for {
          consumer <- consumers
        } yield
          start(consumer.receiveResult.put(DefaultMessageReceiveResult[F, M](pure(message), attributes, unit, unit,
            raiseError(new IllegalStateException("No underlying message")),
            raiseError(new IllegalStateException("No underlying context")))
          ))
      }.sequence
    } yield new MessageSendResult[F, M] {
      def commit = unit

      def rollback = unit
    }

  def stop = unit

  val consumer = InMemoryConsumer[F, M](this)
}

object InMemoryProducer extends CatsUtils {

  def apply[F[_] : Concurrent : Timer, M](consumers: List[InMemoryConsumer[F, M]]): F[InMemoryProducer[F, M]] =
    for {
      consumers <- Ref.of[F, List[InMemoryConsumer[F, M]]](consumers)
    } yield new InMemoryProducer[F, M](consumers)

  def apply[F[_] : Concurrent : Timer, M]: F[InMemoryProducer[F, M]] =
    apply(List())
}