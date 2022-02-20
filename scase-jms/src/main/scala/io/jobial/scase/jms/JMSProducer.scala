package io.jobial.scase.jms

import cats.Monad
import cats.effect.{Concurrent, ContextShift, IO}
import cats.implicits._
import io.jobial.scase.core.{MessageProducer, MessageSendResult}
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshaller

import javax.jms.{BytesMessage, Destination, JMSContext, Session}

class JMSProducer[F[_] : Concurrent, M](destination: Destination)(implicit session: Session)
  extends MessageProducer[F, M] with Logging {

  val producer = session.createProducer(destination)

  override def send(message: M, attributes: Map[String, String])(implicit m: Marshaller[M]): F[MessageSendResult[F, M]] =
    for {
      r <- Concurrent[F].delay {
        val jmsMessage = session.createTextMessage(Marshaller[M].marshalToText(message))
        for {
          (name, value) <- attributes
        } yield jmsMessage.setStringProperty(name, value)
        producer.send(destination, jmsMessage)
      }
    } yield new MessageSendResult[F, M] {
      def commit = Concurrent[F].delay {
        session.commit()
      }

      def rollback = Concurrent[F].delay {
        session.rollback()
      }
    }

  def stop = Concurrent[F].delay(producer.close())
}

object JMSProducer {

  def apply[F[_] : Concurrent, M](destination: Destination)(implicit session: Session) =
    Concurrent[F].delay(new JMSProducer[F, M](destination))
}