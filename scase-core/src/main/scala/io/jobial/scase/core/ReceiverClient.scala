package io.jobial.scase.core

import scala.concurrent.duration.Duration

trait ReceiverClient[F[_], M] {

  def receive(timeout: Option[Duration] = None): F[M]

  def receiveWithContext(timeout: Option[Duration] = None): F[MessageContext[F, M]]
}
