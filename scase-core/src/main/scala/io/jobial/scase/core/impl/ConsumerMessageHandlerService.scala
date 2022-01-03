package io.jobial.scase.core.impl

import cats.effect.Concurrent
import cats.implicits._
import io.jobial.scase.core.{MessageConsumer, MessageContext, MessageHandler, Service, ServiceState}
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Unmarshaller

case class ConsumerMessageHandlerService[F[_] : Concurrent, M: Unmarshaller](
  consumer: MessageConsumer[F, M],
  messageHandler: MessageHandler[F, M]
) extends DefaultService[F] with Logging {

  def start: F[ServiceState[F]] = {
    for {
      subscription <- consumer.subscribe { messageReceiveResult =>
        val messageContext = new MessageContext[F] {

        }
        messageHandler.handleMessage(messageContext)(messageReceiveResult.message)
      }
    } yield
      DefaultServiceState(subscription, this)
  }
}
