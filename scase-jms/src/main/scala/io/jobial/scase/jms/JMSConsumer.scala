package io.jobial.scase.jms

import cats.Monad
import cats.effect.concurrent.Ref
import cats.effect.{Concurrent, IO}
import cats.implicits._
import io.jobial.scase.core.impl.DefaultMessageConsumer
import io.jobial.scase.core.{DefaultMessageReceiveResult, MessageReceiveResult}
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Unmarshaller
import scala.collection.JavaConverters._

import javax.jms._

class JMSConsumer[F[_] : Concurrent, M](destination: Destination, val subscriptions: Ref[F, List[MessageReceiveResult[F, M] => F[_]]])(implicit session: Session)

  extends DefaultMessageConsumer[F, M] with Logging {

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
  
  def receiveMessages[T](callback: MessageReceiveResult[F, M] => F[T], cancelled: Ref[F, Boolean])(implicit u: Unmarshaller[M]): F[Unit] =
    for {
      jmsMessage <- Concurrent[F].liftIO(IO(consumer.receive(1000)))
      _ = logger.debug(s"received message ${jmsMessage.toString.take(200)} on $destination")
      x = unmarshalMessage(jmsMessage)
      _ <- x match {
        case Right(message) =>
          val attributes = extractAttributes(jmsMessage)
          val messageReceiveResult = DefaultMessageReceiveResult(Monad[F].pure(message), attributes, Monad[F].unit, Monad[F].unit)
          callback(messageReceiveResult)
        case Left(error) =>
          // TODO: add logging
          Monad[F].pure(error.printStackTrace)
      }
    } yield ()
}

object JMSConsumer {

  def apply[F[_] : Concurrent, M](destination: Destination)(implicit session: Session) =
    for {
      subscriptions <- Ref.of[F, List[MessageReceiveResult[F, M] => F[_]]](List())
    } yield new JMSConsumer[F, M](destination, subscriptions)
}