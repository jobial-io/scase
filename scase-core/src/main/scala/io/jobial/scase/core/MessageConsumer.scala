package io.jobial.scase.core

import cats.Monad
import cats.effect.{Concurrent, IO}
import io.jobial.scase.marshalling.Unmarshaller

import scala.concurrent.duration._

case class MessageReceiveResult[F[_], M](
  message: M,
  attributes: Map[String, String],
  commit: () => F[Unit], // commit the message
  rollback: () => F[_]
) {

  def correlationId = attributes.get(CorrelationIdKey)

  def requestTimeout = attributes.get(RequestTimeoutKey).map(_.toLong.millis)

  def responseConsumerId = attributes.get(ResponseConsumerIdKey)
}

trait MessageSubscription[F[_], M] {

  def join: F[_]

  def cancel: F[_] // cancel the subscription

  def isCancelled: F[Boolean]
}

trait MessageConsumer[F[_], M] {

  // Subscribes to the message source. In the background, subscribe might start async processing (e.g. a Fiber to poll messages in a source).
  def subscribe[T](callback: MessageReceiveResult[F, M] => F[T])(implicit u: Unmarshaller[M], concurrent: Concurrent[F]): F[MessageSubscription[F, M]]
}

case class CouldNotFindMessageToCommit[M](
  message: M
) extends IllegalStateException