package io.jobial.scase.core

import io.jobial.scase.marshalling.Marshaller

trait MessageSendResult[F[_], M] {
  def commit: F[Unit]

  def rollback: F[Unit]
}

trait MessageProducer[F[_], M] {

  def send(message: M, attributes: Map[String, String] = Map())(implicit m: Marshaller[M]): F[MessageSendResult[F, M]]

  def stop: F[Unit]
}
