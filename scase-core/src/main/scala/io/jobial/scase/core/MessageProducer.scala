package io.jobial.scase.core

import cats.effect.Concurrent
import io.jobial.scase.marshalling.Marshaller

trait MessageSendResult[F[_], M] {
  def commit: F[Unit]

  // TODO: add message id, attributes
}

trait MessageProducer[F[_], M] {

  // TODO: get rid of Concurrent here...
  def send(message: M, attributes: Map[String, String] = Map())(implicit m: Marshaller[M], concurrent: Concurrent[F]): F[MessageSendResult[F, M]]
}
