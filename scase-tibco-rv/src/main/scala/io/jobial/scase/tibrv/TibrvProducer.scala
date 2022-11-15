package io.jobial.scase.tibrv

import cats.implicits._
import com.tibco.tibrv.TibrvMsg
import io.jobial.scase.core.MessageProducer
import io.jobial.scase.core.MessageSendResult
import io.jobial.scase.core.impl.CatsUtils
import io.jobial.scase.core.impl.ConcurrentEffect
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshaller

class TibrvProducer[F[_] : ConcurrentEffect, M](
  val subject: String
)(
  implicit val context: TibrvContext
) extends MessageProducer[F, M] with TibrvSupport with CatsUtils with Logging {

  lazy val transport = createTransport

  def send(message: M, attributes: Map[String, String])(implicit m: Marshaller[M]): F[MessageSendResult[F, M]] =
    for {
      _ <- delay(initRv)
      tibrvMsg = new TibrvMsg(Marshaller[M].marshal(message))
      _ = tibrvMsg.setSendSubject(subject)
      r <- delay(transport.send(tibrvMsg)).onError { case t =>
        error(s"failed to send message on $this", t)
      }
      _ <- trace(s"sent message ${message.toString.take(200)} on $subject")
    } yield new MessageSendResult[F, M] {
      def commit = unit

      def rollback = unit
    }

  def stop = delay(transport.destroy())

  override def toString = super.toString + s" subject: $subject"
}

object TibrvProducer extends CatsUtils {

  def apply[F[_] : ConcurrentEffect, M](
    subject: String
  )(implicit context: TibrvContext) =
    delay(new TibrvProducer[F, M](subject))
}