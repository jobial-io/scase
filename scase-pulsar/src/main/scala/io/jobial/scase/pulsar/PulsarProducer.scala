package io.jobial.scase.pulsar

import cats.effect.{Concurrent, ContextShift, IO}
import cats.implicits._
import io.jobial.scase.core.{MessageProducer, MessageSendResult}
import io.jobial.scase.marshalling.Marshaller

import java.util.UUID.randomUUID
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters.toScala

case class PulsarProducer[F[_], M](topic: String)(implicit context: PulsarContext, cs: ContextShift[IO]) extends MessageProducer[F, M] {

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

  override def send(message: M, attributes: Map[String, String])(implicit m: Marshaller[M], concurrent: Concurrent[F]): F[MessageSendResult[M]] =
    for {
      r <- Concurrent[F].liftIO(IO.fromFuture(IO(toScala(producer
        .newMessage
        .properties(attributes.asJava)
        .value(implicitly[Marshaller[M]].marshal(message))
        .sendAsync()
      ))))
    } yield MessageSendResult[M]()

}