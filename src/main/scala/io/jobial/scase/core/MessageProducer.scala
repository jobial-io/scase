package io.jobial.scase.core

import cats.effect.IO
import io.jobial.scase.marshalling.Marshallable

import scala.concurrent.Future

// TODO: review why we need this; it used to have the messageId returned by the underlying transport, but this is
//  - no longer required.
case class MessageSendResult[M: Marshallable]()

trait MessageProducer[M] {

  def send(message: M, attributes: Map[String, String] = Map())(implicit m: Marshallable[M]): IO[MessageSendResult[M]]
}
