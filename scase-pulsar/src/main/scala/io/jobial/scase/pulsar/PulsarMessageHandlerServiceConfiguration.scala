package io.jobial.scase.pulsar

import cats.effect.{Concurrent, ContextShift, IO, Timer}
import cats.implicits._
import io.jobial.scase.core.impl.{ConsumerMessageHandlerService, ConsumerProducerRequestResponseClient, ConsumerProducerRequestResponseService}
import io.jobial.scase.core.{MessageHandler, MessageHandlerServiceConfiguration, RequestHandler, RequestResponseClient, RequestResponseServiceConfiguration}
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}

import java.util.UUID.randomUUID
import scala.concurrent.duration._

case class PulsarMessageHandlerServiceConfiguration[M: Unmarshaller](
  topic: String
) extends MessageHandlerServiceConfiguration[M] {

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

