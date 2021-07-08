package io.jobial.scase.core

trait SinkService[F[_], M] {

  def handle(message: M): F[Unit]
}
