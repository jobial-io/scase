package io.jobial.scase.core

import io.jobial.scase.marshalling.Unmarshaller

import scala.concurrent.duration._

trait MessageReceiveResult[F[_], M] {

  // In F to allow error handling
  def message: F[M]

  def attributes: Map[String, String]

  def consumer: Option[MessageConsumer[F, _ >: M]]

  def correlationId = attributes.get(CorrelationIdKey)

  def requestTimeout = attributes.get(RequestTimeoutKey).map(_.toLong.millis)

  def responseProducerId = attributes.get(ResponseProducerIdKey).orElse(attributes.get(ResponseTopicKey))

  def commit: F[Unit]

  def rollback: F[Unit]

  def underlyingMessage[T]: F[T]

  def underlyingContext[T]: F[T]
}

case class DefaultMessageReceiveResult[F[_], M](
  message: F[M],
  attributes: Map[String, String],
  consumer: Option[MessageConsumer[F, _ >: M]],
  commit: F[Unit],
  rollback: F[Unit],
  underlyingMessageProvided: F[Any],
  underlyingContextProvided: F[Any]
) extends MessageReceiveResult[F, M] {
  override def underlyingMessage[T]: F[T] = underlyingMessageProvided.asInstanceOf[F[T]]

  override def underlyingContext[T]: F[T] = underlyingContextProvided.asInstanceOf[F[T]]
}

trait MessageSubscription[F[_], M] {

  def join: F[Unit]

  def cancel: F[Unit] // cancel the subscription

  def isCancelled: F[Boolean]
}

/**
 * The usual semantics of a consumer is that each message is delivered to exactly one receive or subscription. Therefore,
 * multiple subscriptions on a consumer receive messages randomly.
 * If messages need to be delivered multiple times, each receiver should have a separate consumer.
 *
 * @tparam F
 * @tparam M
 */
trait MessageConsumer[F[_], M] {

  /**
   * Receive a message from the consumer. Receives compete for messages, each message is returned by exactly one receive.
   *
   * @param timeout
   * @param u
   * @return
   */
  def receive(timeout: Option[FiniteDuration])(implicit u: Unmarshaller[M]): F[MessageReceiveResult[F, M]]

  /**
   * Receive messages continuously and call the provided callback function for each message. If there are multiple
   * subscriptions, they will compete for messages and each message will be delivered to exactly one callback function.
   *
   * @param callback
   * @param u
   * @tparam T
   * @return
   */
  def subscribe[T](callback: MessageReceiveResult[F, M] => F[T])(implicit u: Unmarshaller[M]): F[MessageSubscription[F, M]]

  def stop: F[Unit]
}

case class ReceiveTimeout(
  timeout: Option[Duration],
  cause: Throwable
) extends IllegalStateException(s"receive timed out after $timeout", cause)

object ReceiveTimeout {

  def apply(timeout: Option[Duration]): ReceiveTimeout = ReceiveTimeout(timeout, null)
}

case class CouldNotFindMessageToCommit[M](
  message: M
) extends IllegalStateException