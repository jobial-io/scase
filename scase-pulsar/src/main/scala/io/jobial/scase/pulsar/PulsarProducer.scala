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

  val producer =
    context
      .client
      .newProducer
      .producerName(s"producer-${randomUUID}")
      .topic(context.fullyQualifiedTopicName(topic))
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
      )).handleErrorWith { t =>
        error(s"failed to send message on $this", t) >> raiseError(t)
      }
      _ <- debug(s"sent message ${message.toString.take(200)} on $topic")
    } yield new MessageSendResult[F, M] {
      def commit = unit

      def rollback = unit
    }

  def stop = delay(producer.close())
  
  override def toString = super.toString + s" topic: $topic"
}

object PulsarProducer extends CatsUtils {

  def apply[F[_] : Concurrent, M](topic: String)(implicit context: PulsarContext) =
    delay(new PulsarProducer[F, M](topic))
}