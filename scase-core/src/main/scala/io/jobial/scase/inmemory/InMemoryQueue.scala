package io.jobial.scase.inmemory

import cats.Monad
import cats.implicits._
import cats.effect.{ContextShift, IO, Sync}
import cats.effect.concurrent.Ref
import io.jobial.scase.core.{MessageReceiveResult, Queue}


/**
 * In-memory queue implementation.
 */
case class InMemoryQueue[F[_], M](
  subscriptions: Ref[F, List[MessageReceiveResult[F, M] => F[_]]],
  deliverToAllSubscribers: Boolean = true,
  allowMultipleSubscribers: Boolean = false
) extends Queue[F, M] with InMemoryConsumerProducer[F, M]

object InMemoryQueue {

  def apply[F[_] : Sync, M]: F[InMemoryQueue[F, M]] = for {
    subscriptions <- Ref.of[F, List[MessageReceiveResult[F, M] => F[_]]](List())
  } yield InMemoryQueue[F, M](subscriptions)
}