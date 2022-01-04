package io.jobial.scase.pulsar

import cats.effect.{Concurrent, ContextShift, IO}
import cats.implicits._
import io.jobial.scase.core.impl.ConsumerMessageHandlerService
import io.jobial.scase.core.{MessageHandler, ServiceConfiguration}
import io.jobial.scase.marshalling.Unmarshaller

case class PulsarMessageHandlerServiceConfiguration[M: Unmarshaller](
  serviceName: String,
  topic: String
) extends ServiceConfiguration {

  def service[F[_] : Concurrent](handler: MessageHandler[F, M])
    (
      implicit context: PulsarContext,
      cs: ContextShift[IO]
    ) =
    for {
      consumer <- PulsarConsumer[F, M](topic)
      service = ConsumerMessageHandlerService(
        consumer,
        handler
      )
    } yield service

}

