package io.jobial.scase.pulsar

import cats.implicits._
import io.jobial.scase.core.DefaultMessageReceiveResult
import io.jobial.scase.core.ReceiveTimeout
import io.jobial.scase.core.impl.AsyncEffect
import io.jobial.scase.core.impl.CatsUtils
import io.jobial.scase.core.impl.DefaultMessageConsumer
import io.jobial.scase.core.impl.RegexUtils
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Unmarshaller
import org.apache.pulsar.client.api.ConsumerBuilder
import org.apache.pulsar.client.api.SubscriptionInitialPosition
import java.time.Instant
import java.util.UUID.randomUUID
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters.toScala
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.TimeoutException
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.util.matching.Regex

class PulsarConsumer[F[_] : AsyncEffect, M](
  val topic: Either[String, Regex],
  val patternAutoDiscoveryPeriod: Option[FiniteDuration],
  val subscriptionInitialPosition: Option[SubscriptionInitialPosition],
  val subscriptionInitialPublishTime: Option[Instant],
  val subscriptionName: String
)(implicit context: PulsarContext)
  extends DefaultMessageConsumer[F, M] with CatsUtils with RegexUtils with Logging {

  implicit class ConsumerBuilderExt[T](builder: ConsumerBuilder[T]) {
    def apply(f: ConsumerBuilder[T] => Option[ConsumerBuilder[T]]): ConsumerBuilder[T] =
      f(builder).getOrElse(builder)
  }

  val consumer =
    context
      .client
      .newConsumer
      .consumerName(s"consumer-${randomUUID}")
      .subscriptionName(subscriptionName)
      .apply(b =>
        topic match {
          case Left(topic) =>
            logger.trace(s"using simple topic for $topic")
            Some(b.topic(context.fullyQualifiedTopicName(topic)))
          case Right(topic) =>
            logger.trace(s"using topic pattern for $topic")
            Some(b.topicsPattern(context.fullyQualifiedTopicName(topic.toString)))
        }
      )
      .apply(b =>
        patternAutoDiscoveryPeriod.map(p => b.patternAutoDiscoveryPeriod(p.toSeconds.toInt, TimeUnit.SECONDS))
      )
      .apply(b =>
        subscriptionInitialPosition.map(b.subscriptionInitialPosition)
      )
      .subscribe()

  sys.addShutdownHook { () =>
    if (consumer.isConnected)
      consumer.unsubscribe()
  }

  def receive(timeout: Option[FiniteDuration])(implicit u: Unmarshaller[M]) =
    for {
      pulsarMessage <- fromFuture(toScala {
        val f = consumer.receiveAsync
        timeout.map(t => f.orTimeout(t.toMillis, TimeUnit.MILLISECONDS)).getOrElse(f)
      }).handleErrorWith {
        case t: TimeoutException =>
          trace(s"Receive timed out after $timeout in $this ${consumer.isConnected} context: $context") >>
            raiseError(ReceiveTimeout(timeout, t))
        case t =>
          raiseError(t)
      }
      _ <- trace(s"received message ${pulsarMessage.getMessageId} in $this")
      result <-
        if (subscriptionInitialPublishTime.map(pulsarMessage.getPublishTime < _.toEpochMilli).getOrElse(false))
          trace(s"dropping message ${new String(pulsarMessage.getData).take(200)} with publish time ${pulsarMessage.getPublishTime} < $subscriptionInitialPublishTime on $topic") >>
            raiseError(ReceiveTimeout(timeout))
        else for {
          result <- Unmarshaller[M].unmarshal(pulsarMessage.getData) match {
            case Right(message) =>
              val attributes = pulsarMessage.getProperties.asScala.toMap
              trace(s"unmarshalled message ${message.toString.take(200)} in $this") >>
                pure(
                  DefaultMessageReceiveResult[F, M](
                    pure(message),
                    attributes,
                    Some(this),
                    commit = trace(s"committing message $message in $this") >> delay(consumer.acknowledge(pulsarMessage)),
                    rollback = trace(s"rolling back message $message in $this") >> delay(consumer.negativeAcknowledge(pulsarMessage)),
                    underlyingMessageProvided = pure(pulsarMessage),
                    underlyingContextProvided = raiseError(new IllegalStateException("No underlying context"))
                  )
                )
            case Left(error) =>
              raiseError(error)
          }
        } yield result
    } yield result

  def stop =
    delay(consumer.unsubscribe()) >>
      delay(consumer.close())

  override def toString = super.toString + s" topic: ${context.fullyQualifiedTopicName(topic.left.toOption.getOrElse(topic.right.get.toString))} subscription: $subscriptionName"
}

object PulsarConsumer extends CatsUtils with Logging {

  def apply[F[_] : AsyncEffect, M](
    topic: Either[String, Regex],
    patternAutoDiscoveryPeriod: Option[FiniteDuration] = Some(1.second),
    subscriptionInitialPosition: Option[SubscriptionInitialPosition] = None,
    subscriptionInitialPublishTime: Option[Instant] = None,
    subscriptionName: String = s"subscription-${randomUUID}"
  )(implicit context: PulsarContext): F[PulsarConsumer[F, M]] =
    delay(new PulsarConsumer[F, M](
      topic,
      patternAutoDiscoveryPeriod,
      subscriptionInitialPosition,
      subscriptionInitialPublishTime,
      subscriptionName
    ))
}