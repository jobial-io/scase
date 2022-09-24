package io.jobial.scase.pulsar

import cats.Monad
import cats.effect.{Concurrent, ContextShift, IO}
import cats.implicits._
import io.jobial.scase.core.{MessageProducer, MessageSendResult}
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshaller

import java.util.UUID.randomUUID
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters.toScala

class PulsarProducer[F[_] : Concurrent, M](topic: String)(implicit context: PulsarContext, cs: ContextShift[IO])
  extends MessageProducer[F, M] with Logging {

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

  override def send(message: M, attributes: Map[String, String])(implicit m: Marshaller[M]): F[MessageSendResult[F, M]] =
    for {
      // TODO: could avoid IO here and just use Concurrent[F].async
      r <- Concurrent[F].liftIO(IO.fromFuture(IO(toScala(producer
        .newMessage
        .properties(attributes.asJava)
        .value(Marshaller[M].marshal(message))
        .sendAsync()
      ))))
      _ = logger.info(s"sent message ${message.toString.take(200)}")
    } yield new MessageSendResult[F, M] {
      def commit = Monad[F].unit

      def rollback = Monad[F].unit
    }

  def stop = Concurrent[F].delay(producer.close())
}

object PulsarProducer {

  def apply[F[_] : Concurrent, M](topic: String)(implicit context: PulsarContext, cs: ContextShift[IO]) =
    Concurrent[F].delay(new PulsarProducer[F, M](topic))
}