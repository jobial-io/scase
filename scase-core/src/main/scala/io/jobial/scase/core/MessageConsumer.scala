package io.jobial.scase.core

import cats.effect.Concurrent
import io.jobial.scase.marshalling.Unmarshaller

import scala.concurrent.duration._

trait MessageReceiveResult[F[_], M] {

  // This is in F to allow error handling
  def message: F[M]

  def attributes: Map[String, String]

  def correlationId = attributes.get(CorrelationIdKey)

  def requestTimeout = attributes.get(RequestTimeoutKey).map(_.toLong.millis)

  def responseProducerId = attributes.get(ResponseProducerIdKey)

  def commit: F[Unit]

  def rollback: F[Unit]
}

case class DefaultMessageReceiveResult[F[_], M](
  message: F[M],
  attributes: Map[String, String],
  commit: F[Unit],
  rollback: F[Unit]
) extends MessageReceiveResult[F, M]

trait MessageSubscription[F[_], M] {

  def join: F[Unit]

  def cancel: F[Unit] // cancel the subscription

  def isCancelled: F[Boolean]
}

trait MessageConsumer[F[_], M] {

  // Subscribes to the message source. In the background, subscribe might start async processing (e.g. a Fiber to poll messages in a source).
  // TODO: get rid of Concurrent
  // TODO: add callback option for error result (e.g. unmarshalling error)?
  def subscribe[T](callback: MessageReceiveResult[F, M] => F[T])(implicit u: Unmarshaller[M], concurrent: Concurrent[F]): F[MessageSubscription[F, M]]
}

case class CouldNotFindMessageToCommit[M](
  message: M
) extends IllegalStateException