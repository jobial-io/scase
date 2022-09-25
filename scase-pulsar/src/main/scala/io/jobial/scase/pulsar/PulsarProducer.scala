package io.jobial.scase.pulsar

import cats.Monad
import cats.effect.Concurrent
import cats.implicits._
import io.jobial.scase.core.MessageProducer
import io.jobial.scase.core.MessageSendResult
import io.jobial.scase.core.impl.CatsUtils
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshaller
import java.util.UUID.randomUUID
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters.toScala

class PulsarProducer[F[_] : Concurrent, M](topic: String)(implicit context: PulsarContext)
  extends MessageProducer[F, M] with CatsUtils with Logging {

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
      r <- fromFuture(toScala(producer
        .newMessage
        .properties(attributes.asJava)
        .value(Marshaller[M].marshal(message))
        .sendAsync()
      ))
      _ <- debug[F](s"sent message ${message.toString.take(200)} on $topic")
    } yield new MessageSendResult[F, M] {
      def commit = Monad[F].unit

      def rollback = Monad[F].unit
    }

  def stop = Concurrent[F].delay(producer.close())
}

object PulsarProducer {

  def apply[F[_] : Concurrent, M](topic: String)(implicit context: PulsarContext) =
    Concurrent[F].delay(new PulsarProducer[F, M](topic))
}