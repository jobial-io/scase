package io.jobial.scase.core

import cats.Monad
import cats.effect.{Concurrent, IO}
import io.jobial.scase.marshalling.Marshaller

// TODO: review why we need this; it used to have the messageId returned by the underlying transport.
case class MessageSendResult[M: Marshaller]()

trait MessageProducer[F[_], M] {

  def send(message: M, attributes: Map[String, String] = Map())(implicit m: Marshaller[M], concurrent: Concurrent[F]): F[MessageSendResult[M]]
}
