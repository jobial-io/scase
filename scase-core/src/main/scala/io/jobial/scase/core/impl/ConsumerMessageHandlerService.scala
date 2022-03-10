package io.jobial.scase.core.impl

import cats.effect.Concurrent
import cats.implicits._
import io.jobial.scase.core.{MessageConsumer, MessageContext, MessageHandler, Service, ServiceState}
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Unmarshaller

// TODO: rename to OneWayService
class ConsumerMessageHandlerService[F[_] : Concurrent, M: Unmarshaller](
  consumer: MessageConsumer[F, M],
  messageHandler: MessageHandler[F, M]
) extends DefaultService[F] with Logging {

  def start: F[ServiceState[F]] = {
    for {
      subscription <- consumer.subscribe { messageReceiveResult =>
        val messageContext = new MessageContext[F] {}
        for {
          message <- messageReceiveResult.message
          result <- messageHandler.handleMessage(messageContext)(message)
        } yield result
      }
    } yield
      DefaultServiceState(subscription, this)
  }
}
