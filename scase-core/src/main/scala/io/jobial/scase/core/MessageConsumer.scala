package io.jobial.scase.core

import cats.effect.IO
import io.jobial.scase.marshalling.Unmarshaller

import scala.concurrent.duration._

case class MessageReceiveResult[M](
  message: M,
  attributes: Map[String, String],
  commit: () => IO[_], // commit the message
  rollback: () => IO[_]
) {

  def correlationId = attributes.get(CorrelationIdKey)

  def requestTimeout = attributes.get(RequestTimeoutKey).map(_.toLong millis)

  def responseConsumerId = attributes.get(ResponseConsumerIdKey)
}

trait MessageSubscription[M] {

  def join: IO[_]

  def cancel: IO[_] // cancel the subscription

  def isCancelled: IO[Boolean]
}

trait MessageConsumer[M] {

  // Subscribes to the message source. In the background, subscribe might start async processing (e.g. a Fiber to poll messages in a source).
  def subscribe[T](callback: MessageReceiveResult[M] => IO[T])(implicit u: Unmarshaller[M]): IO[MessageSubscription[M]]
}

case class CouldNotFindMessageToCommit[M](
  message: M
) extends IllegalStateException