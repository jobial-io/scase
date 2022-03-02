package io.jobial.scase.core.impl

import cats.effect.Concurrent
import cats.implicits._
import io.jobial.scase.core.{MessageConsumer, MessageContext, MessageHandler, ServiceState}
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Unmarshaller

//// TODO: rename to OneWayService
//class ConsumerMessageSource[F[_] : Concurrent, M: Unmarshaller](
//  consumer: MessageConsumer[F, M]
//) extends DefaultService[F] with Logging {
//
//  def start: F[ServiceState[F]] = {
//    for {
//      subscription <- consumer.subscribe { messageReceiveResult =>
//        val messageContext = new MessageContext[F, M] {}
//        for {
//          message <- messageReceiveResult.message
//        } yield messageHandler.handle(messageContext)(message)
//      }
//    } yield
//      DefaultServiceState(subscription, this)
//  }
//  
//  
//}
