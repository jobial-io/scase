package io.jobial.scase.jms

import cats.Monad
import cats.implicits._
import cats.effect.{Concurrent, Sync}
import cats.effect.concurrent.Ref
import io.jobial.scase.core.{DefaultMessageReceiveResult, MessageReceiveResult}
import io.jobial.scase.core.impl.DefaultMessageConsumer
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Unmarshaller

import javax.jms.{BytesMessage, Destination, JMSContext, Message, MessageListener, TextMessage}

class JMSConsumer[F[_] : Concurrent, M](destination: Destination, val subscriptions: Ref[F, List[MessageReceiveResult[F, M] => F[_]]])(implicit context: JMSContext)
  extends DefaultMessageConsumer[F, M] with Logging {

  val consumer = context.createConsumer(destination)

  override def receiveMessages[T](callback: MessageReceiveResult[F, M] => F[T], cancelled: Ref[F, Boolean])(implicit u: Unmarshaller[M]): F[Unit] =
    Concurrent[F].pure(consumer.setMessageListener(new MessageListener {

      def unmarshalMessage(message: Message) = message match {
        case m: TextMessage =>
          Unmarshaller[M].unmarshalFromText(m.getText)
        case m: BytesMessage =>
          // TODO: read bytes and unmarshal
          // Unmarshaller[M].unmarshalFromText(m.getB)
          ???
        case m: BytesMessage =>
          // TODO: read bytes and unmarshal
          ???
      }

      def extractAttributes(message: Message): Map[String, String] = Map()

      def onMessage(message: Message) =
        for {
          c <- cancelled.get
        } yield
          if (c)
            consumer.setMessageListener(null)
          else
            callback(
              DefaultMessageReceiveResult(
                Concurrent[F].fromEither(unmarshalMessage(message)),
                extractAttributes(message),
                Concurrent[F].delay(context.commit()),
                Concurrent[F].delay(context.rollback())
              )
            )
    }))

}

object JMSConsumer {

  def apply[F[_] : Concurrent, M](destination: Destination)(implicit context: JMSContext) =
    for {
      subscriptions <- Ref.of[F, List[MessageReceiveResult[F, M] => F[_]]](List())
    } yield new JMSConsumer[F, M](destination, subscriptions)
}