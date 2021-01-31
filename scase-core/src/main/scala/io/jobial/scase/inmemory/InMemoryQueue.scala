package io.jobial.scase.inmemory

import cats.effect.{ContextShift, IO}
import cats.effect.concurrent.Ref
import io.jobial.scase.core.{MessageReceiveResult, Queue}


/**
 * In-memory queue implementation.
 */
case class InMemoryQueue[M](
  subscriptions: Ref[IO, List[MessageReceiveResult[M] => IO[_]]],
  deliverToAllSubscribers: Boolean = true,
  allowMultipleSubscribers: Boolean = false
)(
  implicit val cs: ContextShift[IO]
) extends Queue[M] with InMemoryConsumerProducer[M]

object InMemoryQueue {

  def apply[M](implicit cs: ContextShift[IO]): IO[InMemoryQueue[M]] = for {
    subscriptions <- Ref.of[IO, List[MessageReceiveResult[M] => IO[_]]](List())
  } yield InMemoryQueue(subscriptions)
}