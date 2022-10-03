package io.jobial.scase.pulsar

import cats.effect.Concurrent
import cats.effect.Timer
import cats.effect.concurrent.Ref
import cats.implicits._
import io.jobial.scase.core.DefaultMessageReceiveResult
import io.jobial.scase.core.MessageReceiveResult
import io.jobial.scase.core.MessageSubscription
import io.jobial.scase.core.ReceiveTimeout
import io.jobial.scase.core.impl.CatsUtils
import io.jobial.scase.core.impl.DefaultMessageConsumer
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Unmarshaller
import java.util.UUID.randomUUID
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters.toScala
import scala.concurrent.TimeoutException
import scala.concurrent.duration.FiniteDuration


class PulsarConsumer[F[_] : Concurrent : Timer, M](topic: String, val subscriptions: Ref[F, List[MessageReceiveResult[F, M] => F[_]]])(implicit context: PulsarContext)
  extends DefaultMessageConsumer[F, M] with CatsUtils with Logging {

  val subscriptionName = s"$topic-subscription-${randomUUID}"

  val responseTopicInNamespace = context.topicInDefaultNamespace(topic)

  val consumer =
    context
      .client
      .newConsumer
      .consumerName(s"consumer-${randomUUID}")
      .topic(topic)
      .subscriptionName(subscriptionName)
      .subscribe

  sys.addShutdownHook { () =>
    if (consumer.isConnected)
      consumer.unsubscribe()
  }

  def receive(timeout: Option[FiniteDuration])(implicit u: Unmarshaller[M]) =
    for {
      pulsarMessage <- fromFuture(toScala {
        if (!consumer.isConnected) println(s"!!!!not connected $this")
        val f = consumer.receiveAsync
        timeout.map(t => f.orTimeout(t.toNanos, TimeUnit.NANOSECONDS)).getOrElse(f)
      }).handleErrorWith {
        case t: TimeoutException =>
          debug(s"Receive timed out after $timeout") >>
            raiseError(ReceiveTimeout(timeout, t))
        case t =>
          raiseError(t)
      }
      _ <- debug(s"received message ${new String(pulsarMessage.getData).take(200)} on $topic")
      unmarshalledMessage = Unmarshaller[M].unmarshal(pulsarMessage.getData)
      result <- unmarshalledMessage match {
        case Right(message) =>
          val attributes = pulsarMessage.getProperties.asScala.toMap
          pure(
            DefaultMessageReceiveResult[F, M](pure(message), attributes,
              commit = delay(consumer.acknowledge(pulsarMessage)),
              rollback = delay(consumer.negativeAcknowledge(pulsarMessage)),
              underlyingMessage = pure(pulsarMessage),
              underlyingContext = raiseError(new IllegalStateException("No underlying context"))
            )
          )
        case Left(error) =>
          raiseError(error)
      }
    } yield result

  def stop =
    delay(consumer.unsubscribe()) >>
      delay(consumer.close())
      
  override def toString = super.toString + s" topic: $topic subscription: $subscriptionName"
}

object PulsarConsumer {

  def apply[F[_] : Concurrent : Timer, M](topic: String)(implicit context: PulsarContext): F[PulsarConsumer[F, M]] =
    for {
      subscriptions <- Ref.of[F, List[MessageReceiveResult[F, M] => F[_]]](List())
    } yield new PulsarConsumer[F, M](topic, subscriptions)
}