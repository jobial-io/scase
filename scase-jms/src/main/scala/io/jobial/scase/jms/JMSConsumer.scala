package io.jobial.scase.jms

import cats.effect.Concurrent
import cats.effect.concurrent.Ref
import cats.implicits._
import io.jobial.scase.core.DefaultMessageReceiveResult
import io.jobial.scase.core.MessageReceiveResult
import io.jobial.scase.core.ReceiveTimeout
import io.jobial.scase.core.impl.DefaultMessageConsumer
import io.jobial.scase.marshalling.Unmarshaller
import java.time.Instant.ofEpochMilli
import javax.jms._
import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration

class JMSConsumer[F[_] : Concurrent, M](destination: Destination, val subscriptions: Ref[F, List[MessageReceiveResult[F, M] => F[_]]])(implicit session: Session)
  extends DefaultMessageConsumer[F, M] {

  val consumer = session.createConsumer(destination)

  def unmarshalMessage(message: Message)(implicit u: Unmarshaller[M]) = message match {
    case m: TextMessage =>
      Unmarshaller[M].unmarshalFromText(m.getText)
    case m: BytesMessage =>
      // TODO: fix this
      val buf = new Array[Byte](m.getBodyLength.toInt)
      m.readBytes(buf)
      Unmarshaller[M].unmarshal(buf)
    case m: ObjectMessage =>
      ???
    case _ =>
      // Not supported
      ???
  }

  def extractAttributes(message: Message): Map[String, String] =
    message.getPropertyNames.asScala.map(_.toString).map { name =>
      name -> message.getStringProperty(name)
    }.toMap

  def receive(timeout: Option[FiniteDuration])(implicit u: Unmarshaller[M]) =
    for {
      jmsMessage <- delay(Option(consumer.receive(timeout.map(_.toMillis).getOrElse(Long.MaxValue)))).handleErrorWith {
        case t: JMSException =>
          raiseError(ReceiveTimeout(timeout, t))
        case t =>
          error(s"error receiving message", t) >>
            raiseError(t)
      }
      _ <- trace(s"received message ${jmsMessage.toString.take(200)} on $destination")
      result <- (for {
        jmsMessage <- jmsMessage
        message = unmarshalMessage(jmsMessage)
      } yield message match {
        case Right(message) =>
          val attributes = extractAttributes(jmsMessage)
          val messageReceiveResult = DefaultMessageReceiveResult[F, M](
            pure(message),
            attributes,
            Some(this),
            commit = whenA(session.getTransacted)(delay(session.commit)),
            rollback = whenA(session.getTransacted)(delay(session.rollback)),
            underlyingMessageProvided = pure(jmsMessage),
            underlyingContextProvided = raiseError(new IllegalStateException("No underlying context")),
            delay(jmsMessage.getJMSDestination.toString),
            delay(ofEpochMilli(jmsMessage.getJMSTimestamp))
          )
          pure(messageReceiveResult)
        case Left(error) =>
          raiseError(error)
      }).getOrElse(raiseError(ReceiveTimeout(timeout))) // if null is returned it's a timeout
    } yield result

  def stop = delay(consumer.close())

  override def toString = super.toString + s" destination: $destination"
}

object JMSConsumer {

  def apply[F[_] : Concurrent, M](destination: Destination)(implicit session: Session) =
    for {
      subscriptions <- Ref.of[F, List[MessageReceiveResult[F, M] => F[_]]](List())
    } yield new JMSConsumer[F, M](destination, subscriptions)
}