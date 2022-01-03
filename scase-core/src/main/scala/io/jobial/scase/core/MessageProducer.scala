package io.jobial.scase.core

import cats.effect.Concurrent
import io.jobial.scase.marshalling.Marshaller

// TODO: review why we need this; it used to have the messageId returned by the underlying transport.
case class MessageSendResult[M: Marshaller]()

trait MessageProducer[F[_], M] {

  // TODO: get rid of Concurrent here...
  def send(message: M, attributes: Map[String, String] = Map())(implicit m: Marshaller[M], concurrent: Concurrent[F]): F[MessageSendResult[M]]
}
