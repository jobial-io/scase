package io.jobial.scase.inmemory


import cats.effect.concurrent.Ref
import cats.effect.{ContextShift, IO}
import io.jobial.scase.core.{MessageReceiveResult, Topic}

case class InMemoryTopic[M](
  subscriptions: Ref[IO, List[MessageReceiveResult[M] => IO[_]]]
)(
  implicit val cs: ContextShift[IO]
) extends Topic[M] with InMemoryConsumerProducer[M] {

  val deliverToAllSubscribers = true

  val allowMultipleSubscribers = true
}
