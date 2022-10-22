package io.jobial.scase.tibrv

import cats.effect.Concurrent
import cats.implicits._
import com.tibco.tibrv.Tibrv
import com.tibco.tibrv.TibrvMsg
import com.tibco.tibrv.TibrvRvdTransport
import io.jobial.scase.core.MessageProducer
import io.jobial.scase.core.MessageSendResult
import io.jobial.scase.core.impl.CatsUtils
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshaller

class TibrvProducer[F[_] : Concurrent, M](
  subject: String
)(implicit context: TibrvContext
) extends MessageProducer[F, M] with CatsUtils with Logging {

  lazy val transport =
    new TibrvRvdTransport(context.service.getOrElse(null), context.network.getOrElse(null), s"${context.host}:${context.port}")

  def send(message: M, attributes: Map[String, String])(implicit m: Marshaller[M]): F[MessageSendResult[F, M]] =
    for {
      _ <- delay{
          if (!Tibrv.isValid) Tibrv.open(Tibrv.IMPL_NATIVE)
      }
      tibrvMsg = new TibrvMsg(Marshaller[M].marshal(message))
      _ = tibrvMsg.setSendSubject(subject)
      r <- delay(transport.send(tibrvMsg)).handleErrorWith { t =>
        error(s"failed to send message on $this", t) >> raiseError(t)
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

  def apply[F[_] : Concurrent, M](
    subject: String
  )(implicit context: TibrvContext) =
    delay(new TibrvProducer[F, M](subject))
}