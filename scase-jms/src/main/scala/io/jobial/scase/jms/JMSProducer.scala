package io.jobial.scase.jms

import cats.Monad
import cats.effect.{Concurrent, ContextShift, IO}
import cats.implicits._
import io.jobial.scase.core.{MessageProducer, MessageSendResult}
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshaller

import javax.jms.{Destination, JMSContext}

class JMSProducer[F[_], M](destination: Destination)(implicit context: JMSContext, cs: ContextShift[IO])
  extends MessageProducer[F, M] with Logging {

  val producer = context.createProducer

  override def send(message: M, attributes: Map[String, String])(implicit m: Marshaller[M], concurrent: Concurrent[F]): F[MessageSendResult[F, M]] =
    for {
      r <- Concurrent[F].liftIO(IO(
        producer.send(destination, m.marshal(message))
      ))
    } yield new MessageSendResult[F, M] {
      def commit = Monad[F].unit
    }

}

object JMSProducer {

  def apply[F[_], M](destination: Destination)(implicit context: JMSContext, cs: ContextShift[IO]): JMSProducer[F, M] =
    new JMSProducer(destination)
}