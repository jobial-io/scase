package io.jobial.scase.inmemory


import cats.effect.IO
import cats.effect.concurrent.Ref
import io.jobial.scase.core.{MessageReceiveResult, Topic}

import scala.concurrent.ExecutionContext

case class InMemoryTopic[M](
  subscriptions: Ref[IO, List[MessageReceiveResult[M] => IO[_]]]
) extends Topic[M] with InMemoryConsumerProducer[M] {

  val deliverToAllSubscribers = true

  val allowMultipleSubscribers = true
}
