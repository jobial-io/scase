package io.jobial.scase.pulsar

import cats.effect.Concurrent
import cats.implicits._
import io.jobial.scase.core.MessageProducer
import io.jobial.scase.core.MessageSendResult
import io.jobial.scase.core.impl.CatsUtils
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshaller
import org.apache.pulsar.client.api.ProducerBuilder
import java.util.UUID.randomUUID
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters.toScala
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

class PulsarProducer[F[_] : Concurrent, M](
  topic: String,
  batchingMaxPublishDelay: Option[FiniteDuration]
)(implicit context: PulsarContext)
  extends MessageProducer[F, M] with CatsUtils with Logging {

  implicit class ProducerBuilderExt[T](builder: ProducerBuilder[T]) {
    def apply(f: ProducerBuilder[T] => Option[ProducerBuilder[T]]): ProducerBuilder[T] =
      f(builder).getOrElse(builder)
  }

  val producer =
    context
      .client
      .newProducer
      .producerName(s"producer-${randomUUID}")
      .topic(context.fullyQualifiedTopicName(topic))
      .blockIfQueueFull(true)
      .apply { b =>
        batchingMaxPublishDelay.map(d => b.enableBatching(true).batchingMaxPublishDelay(d.toNanos, TimeUnit.NANOSECONDS))
      }
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
      _ <- trace(s"sent message ${message.toString.take(200)} on ${producer.getTopic} context: $context")
    } yield new MessageSendResult[F, M] {
      def commit = unit

      def rollback = unit
    }

  def stop = delay(producer.close())

  override def toString = super.toString + s" topic: $topic"
}

object PulsarProducer extends CatsUtils {

  def apply[F[_] : Concurrent, M](
    topic: String,
    batchingMaxPublishDelay: Option[FiniteDuration] = Some(1.millis)
  )(implicit context: PulsarContext) =
    delay(new PulsarProducer[F, M](
      topic,
      batchingMaxPublishDelay
    ))
}