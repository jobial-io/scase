package io.jobial.scase.pulsar

import cats.Monad
import cats.effect.concurrent.Ref
import cats.effect.{Concurrent, ContextShift, IO, Sync}
import cats.implicits._
import io.jobial.scase.core.{DefaultMessageConsumer, MessageReceiveResult}
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Unmarshaller

import java.util.UUID.randomUUID
import java.util.concurrent.{CompletableFuture, Executors}
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters.toScala
import scala.concurrent.{ExecutionContext, Future}


case class PulsarConsumer[F[_], M](topic: String, subscriptions: Ref[F, List[MessageReceiveResult[F, M] => F[_]]])(implicit context: PulsarContext, cs: ContextShift[IO])
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

  // TODO: move this out
  implicit val ec = ExecutionContext.fromExecutor(Executors.newCachedThreadPool)

  def concurrentFromFuture[F[_] : Concurrent, T](f: Future[T]): F[T] = ???

  def receiveMessages[T](callback: MessageReceiveResult[F, M] => F[T])(implicit u: Unmarshaller[M], concurrent: Concurrent[F]): F[Unit] =
    for {
      // TODO: eliminate IO here by implementing concurrentFromFuture
      pulsarMessage <- Concurrent[F].liftIO(IO.fromFuture(IO(toScala(consumer.receiveAsync))))
      _ = logger.debug(s"received message ${new String(pulsarMessage.getData).take(200)} on $topic")
      x = Unmarshaller[M].unmarshal(pulsarMessage.getData)
      _ <- x match {
        case Right(message) =>
          val attributes = pulsarMessage.getProperties.asScala.toMap
          val messageReceiveResult = MessageReceiveResult(message, attributes, { () => Monad[F].pure() }, { () => Monad[F].pure() })
          callback(messageReceiveResult)
        case Left(error) =>
          // TODO: add logging
          Monad[F].pure(error.printStackTrace)
      }
      r <- receiveMessages(callback)
    } yield r
}

object PulsarConsumer {

  def apply[F[_] : Sync, M](topic: String)(implicit context: PulsarContext, cs: ContextShift[IO]): F[PulsarConsumer[F, M]] =
    for {
      subscriptions <- Ref.of[F, List[MessageReceiveResult[F, M] => F[_]]](List())
    } yield PulsarConsumer[F, M](topic, subscriptions)
}