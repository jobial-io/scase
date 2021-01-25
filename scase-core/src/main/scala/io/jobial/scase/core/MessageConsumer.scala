package io.jobial.scase.core

import cats.effect.IO
import io.jobial.scase.marshalling.Unmarshaller

import scala.concurrent.Future
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

  def subscription: IO[_]

  def cancel: IO[_] // cancel the subscription
  
  def isCancelled: Boolean
}

trait MessageConsumer[M] {

  def subscribe[T](callback: MessageReceiveResult[M] => T)(implicit u: Unmarshaller[M]): MessageSubscription[M]
}

case class CouldNotFindMessageToCommit[M](
  message: M
) extends IllegalStateException