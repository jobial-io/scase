package io.jobial.scase.jms

import cats.implicits._
import io.jobial.scase.core.MessageProducer
import io.jobial.scase.core.MessageSendResult
import io.jobial.scase.core.impl.CatsUtils
import io.jobial.scase.core.impl.ConcurrentEffect
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshaller
import javax.jms.Destination
import javax.jms.Session

class JMSProducer[F[_] : ConcurrentEffect, M](destination: Destination)(implicit session: Session)
  extends MessageProducer[F, M] with CatsUtils with Logging {

  val producer = session.createProducer(destination)

  override def send(message: M, attributes: Map[String, String])(implicit m: Marshaller[M]): F[MessageSendResult[F, M]] =
    for {
      r <- delay {
        val jmsMessage = session.createTextMessage(Marshaller[M].marshalToText(message))
        for {
          (name, value) <- attributes
        } yield jmsMessage.setStringProperty(name, value)
        producer.send(destination, jmsMessage)
      }
    } yield new MessageSendResult[F, M] {
      def commit = delay {
        session.commit()
      }

      def rollback = delay {
        session.rollback()
      }
    }

  def stop = delay(producer.close())

  override def toString = super.toString + s" destination: $destination"
}

object JMSProducer extends CatsUtils {

  def apply[F[_] : ConcurrentEffect, M](destination: Destination)(implicit session: Session) =
    delay(new JMSProducer[F, M](destination))
}