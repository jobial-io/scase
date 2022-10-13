package io.jobial.scase.core.impl

import cats.effect.Concurrent
import cats.implicits._
import io.jobial.scase.core.MessageReceiveResult
import io.jobial.scase.core.{MessageConsumer, MessageContext, MessageHandler, Service, ServiceState}
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Unmarshaller

// TODO: rename to OneWayService
class ConsumerMessageHandlerService[F[_] : Concurrent, M: Unmarshaller](
  val consumer: MessageConsumer[F, M],
  val messageHandler: MessageHandler[F, M]
) extends DefaultService[F] with Logging {

  def start: F[ServiceState[F]] = {
    for {
      subscription <- consumer.subscribe { messageReceiveResult =>
        val messageContext = new MessageContext[F] {
          def receiveResult[M](request: M) = messageReceiveResult.asInstanceOf[MessageReceiveResult[F, M]]
        }
        for {
          message <- messageReceiveResult.message
          result <- messageHandler.handleMessage(messageContext)(message)
        } yield result
      }
    } yield
      DefaultServiceState(subscription, this)
  }
}

object ConsumerMessageHandlerService extends CatsUtils {

  def apply[F[_] : Concurrent, M: Unmarshaller](
    consumer: MessageConsumer[F, M],
    messageHandler: MessageHandler[F, M]
  ) =
    delay(new ConsumerMessageHandlerService[F, M](consumer, messageHandler))
}