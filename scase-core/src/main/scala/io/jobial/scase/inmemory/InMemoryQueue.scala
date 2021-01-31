package io.jobial.scase.inmemory

import cats.effect.IO
import cats.effect.concurrent.Ref
import io.jobial.scase.core.{MessageReceiveResult, Queue}


/**
 * In-memory queue implementation.
 */
case class InMemoryQueue[M](
  subscriptions: Ref[IO, List[MessageReceiveResult[M] => IO[_]]],
  deliverToAllSubscribers: Boolean = true,
  allowMultipleSubscribers: Boolean = false
) extends Queue[M] with InMemoryConsumerProducer[M]

object InMemoryQueue {

  def apply[M]: IO[InMemoryQueue[M]] = for {
    subscriptions <- Ref.of[IO, List[MessageReceiveResult[M] => IO[_]]](List())
  } yield InMemoryQueue(subscriptions)
}