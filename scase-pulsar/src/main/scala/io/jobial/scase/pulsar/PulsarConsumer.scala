package io.jobial.scase.pulsar

import cats.Monad
import cats.effect.Concurrent
import cats.effect.Timer
import cats.effect.concurrent.Ref
import cats.effect.implicits.catsEffectSyntaxConcurrent
import cats.implicits._
import io.jobial.scase.core.impl.CatsUtils
import io.jobial.scase.core.impl.DefaultMessageConsumer
import io.jobial.scase.core.DefaultMessageReceiveResult
import io.jobial.scase.core.MessageReceiveResult
import io.jobial.scase.core.ReceiveTimeout
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Unmarshaller
import java.util.UUID.randomUUID
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

  sys.addShutdownHook(new Thread {
    override def run() =
      if (consumer.isConnected)
        consumer.unsubscribe()
  })

  def receive(timeout: Option[FiniteDuration])(implicit u: Unmarshaller[M]) =
    for {
      pulsarMessage <- {
        val r = fromFuture(toScala(consumer.receiveAsync))
        timeout.map(t => r.timeout(t) handleErrorWith {
          case t: TimeoutException =>
            debug[F](s"Receive timed out after $timeout") >>
              Concurrent[F].raiseError(ReceiveTimeout(timeout, t))
          case t =>
            Concurrent[F].raiseError(t)
        }).getOrElse(r)
      }
      _ <- debug[F](s"received message ${new String(pulsarMessage.getData).take(200)} on $topic")
      unmarshalledMessage = Unmarshaller[M].unmarshal(pulsarMessage.getData)
      result <- unmarshalledMessage match {
        case Right(message) =>
          val attributes = pulsarMessage.getProperties.asScala.toMap
          Monad[F].pure(
            DefaultMessageReceiveResult(Monad[F].pure(message), attributes,
              commit = Concurrent[F].delay(consumer.acknowledge(pulsarMessage)),
              rollback = Concurrent[F].delay(consumer.negativeAcknowledge(pulsarMessage))
            )
          )
        case Left(error) =>
          Concurrent[F].raiseError(error)
      }
    } yield result

  def stop =
    Concurrent[F].delay(consumer.unsubscribe()) >>
      Concurrent[F].delay(consumer.close())
}

object PulsarConsumer {

  def apply[F[_] : Concurrent : Timer, M](topic: String)(implicit context: PulsarContext): F[PulsarConsumer[F, M]] =
    for {
      subscriptions <- Ref.of[F, List[MessageReceiveResult[F, M] => F[_]]](List())
    } yield new PulsarConsumer[F, M](topic, subscriptions)
}