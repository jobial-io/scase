package io.jobial.scase.pulsar

import cats.Monad
import cats.effect.concurrent.{Deferred, Ref}
import cats.effect.{Concurrent, ContextShift, IO, Sync}
import cats.implicits._
import io.jobial.scase.core.{DefaultMessageReceiveResult, MessageReceiveResult}
import io.jobial.scase.core.impl.DefaultMessageConsumer
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Unmarshaller

import java.util.UUID.randomUUID
import java.util.concurrent.CompletableFuture
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters.toScala


class PulsarConsumer[F[_] : Concurrent, M](topic: String, val subscriptions: Ref[F, List[MessageReceiveResult[F, M] => F[_]]])(implicit context: PulsarContext, cs: ContextShift[IO])
  extends DefaultMessageConsumer[F, M] with Logging {

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

  implicit def toScalaFuture[T](f: CompletableFuture[T]) = toScala[T](f)

  def receiveMessages[T](callback: MessageReceiveResult[F, M] => F[T], cancelled: Ref[F, Boolean])(implicit u: Unmarshaller[M]): F[Unit] =
    for {
      // TODO: could avoid IO here and just use Concurrent[F].async
      pulsarMessage <- Concurrent[F].liftIO(IO.fromFuture(IO(toScala(consumer.receiveAsync))))
      _ = logger.debug(s"received message ${new String(pulsarMessage.getData).take(200)} on $topic")
      x = Unmarshaller[M].unmarshal(pulsarMessage.getData)
      _ <- x match {
        case Right(message) =>
          val attributes = pulsarMessage.getProperties.asScala.toMap
          val messageReceiveResult = DefaultMessageReceiveResult(Monad[F].pure(message), attributes, Monad[F].unit, Monad[F].unit)
          callback(messageReceiveResult)
        case Left(error) =>
          Concurrent[F].delay(logger.error("failed to unmarshal message", error))
      }
    } yield ()

  def stop = Concurrent[F].delay(consumer.close())
}

object PulsarConsumer {

  def apply[F[_] : Concurrent, M](topic: String)(implicit context: PulsarContext, cs: ContextShift[IO]): F[PulsarConsumer[F, M]] =
    for {
      subscriptions <- Ref.of[F, List[MessageReceiveResult[F, M] => F[_]]](List())
    } yield new PulsarConsumer[F, M](topic, subscriptions)
}