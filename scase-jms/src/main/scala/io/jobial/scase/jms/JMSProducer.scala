package io.jobial.scase.jms

import cats.Monad
import cats.effect.{Concurrent, ContextShift, IO}
import cats.implicits._
import io.jobial.scase.core.{MessageProducer, MessageSendResult}
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshaller

import javax.jms.{BytesMessage, Destination, JMSContext, Session}

class JMSProducer[F[_] : Concurrent, M](destination: Destination)(implicit session: Session, cs: ContextShift[IO])
  extends MessageProducer[F, M] with Logging {

  val producer = session.createProducer(destination)

  override def send(message: M, attributes: Map[String, String])(implicit m: Marshaller[M]): F[MessageSendResult[F, M]] =
    for {
      r <- Concurrent[F].liftIO(IO {
        val jmsMessage = session.createTextMessage(Marshaller[M].marshalToText(message))
        for {
          (name, value) <- attributes
        } yield jmsMessage.setStringProperty(name, value)
        producer.send(destination, jmsMessage)
      })
    } yield new MessageSendResult[F, M] {
      def commit = Monad[F].unit

      def rollback = Monad[F].unit
    }

}

object JMSProducer {

  def apply[F[_] : Concurrent, M](destination: Destination)(implicit session: Session, cs: ContextShift[IO]): JMSProducer[F, M] =
    new JMSProducer(destination)
}