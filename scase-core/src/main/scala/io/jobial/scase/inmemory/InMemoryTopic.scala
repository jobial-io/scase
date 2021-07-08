package io.jobial.scase.inmemory


import cats.effect.concurrent.Ref
import cats.effect.{ContextShift, IO}
import io.jobial.scase.core.{MessageReceiveResult, Topic}

case class InMemoryTopic[F[_], M](
  subscriptions: Ref[F, List[MessageReceiveResult[F, M] => F[_]]]
)(
  implicit val cs: ContextShift[IO]
) extends Topic[F, M] with InMemoryConsumerProducer[F, M] {

  val deliverToAllSubscribers = true

  val allowMultipleSubscribers = true
}
