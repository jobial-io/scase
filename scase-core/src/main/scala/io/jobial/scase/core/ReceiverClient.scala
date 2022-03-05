package io.jobial.scase.core

import scala.concurrent.duration.FiniteDuration

trait ReceiverClient[F[_], M] {

  def receive: F[M]

  def receive(timeout: FiniteDuration): F[M]

  def receiveWithContext: F[MessageReceiveResult[F, M]]

  def receiveWithContext(timeout: FiniteDuration): F[MessageReceiveResult[F, M]]

  def stop: F[Unit]
}
