package io.jobial.scase.core

import scala.concurrent.duration.Duration

trait MessageSource[F[_], M] {

  type Handler = Function[M, F[Unit]]

  def receive(timeout: Option[Duration] = None): F[M]

  def receiveWithContext(timeout: Option[Duration] = None): F[MessageContext[F, M]]
}
