package io.jobial.scase.pulsar.client

import cats.Monad
import cats.effect.{Concurrent, Sync}
import io.jobial.scase.core.{MessageProducer, MessageSendResult}
import io.jobial.scase.marshalling.Marshaller

import java.util.UUID.randomUUID
import java.util.concurrent.TimeUnit

case class PulsarProducer[F[_], M](topic: String)(implicit context: PulsarContext) extends MessageProducer[F, M] {

  lazy val producer =
    context
      .client
      .newProducer
      .producerName(s"producer-${randomUUID}")
      .topic(topic)
      .blockIfQueueFull(true)
      .batchingMaxPublishDelay(1, TimeUnit.MILLISECONDS)
      .enableBatching(true)
      .create
      
  override def send(message: M, attributes: Map[String, String])(implicit m: Marshaller[M], concurrent: Concurrent[F]): F[MessageSendResult[M]] = ???
}

//object PulsarProducer {
//
//  def apply[F[_] : Sync, M](topic: String): F[PulsarProducer[F, M]] =
//    Monad[F].pure(PulsarProducer[F, M](topic))
//}