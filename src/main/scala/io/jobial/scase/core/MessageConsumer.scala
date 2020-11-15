package io.jobial.scase.core

import io.jobial.scase.marshalling.Marshallable

import scala.concurrent.Future
import scala.concurrent.duration._

case class MessageReceiveResult[M](
  message: M,
  attributes: Map[String, String],
  commit: () => Future[_], // commit the message
  rollback: () => Future[_]
) {

  def correlationId = attributes.get(CorrelationIdKey)

  def requestTimeout = attributes.get(RequestTimeoutKey).map(_.toLong millis)
  
  def responseConsumerId = attributes.get(ResponseConsumerIdKey)
}

trait MessageSubscription[M] {

  def subscription: Future[_]

  def cancel: Future[_] // cancel the subscription
  
  def isCancelled: Boolean
}

trait MessageConsumer[M] {

  def subscribe[T](callback: MessageReceiveResult[M] => T)(implicit u: Marshallable[M]): MessageSubscription[M]
}

case class CouldNotFindMessageToCommit[M](
  message: M
) extends IllegalStateException