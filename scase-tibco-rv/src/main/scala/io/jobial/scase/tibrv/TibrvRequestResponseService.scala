package io.jobial.scase.tibrv

import cats.effect.Concurrent
import cats.effect.concurrent.Deferred
import cats.implicits._
import com.tibco.tibrv.TibrvListener
import com.tibco.tibrv.TibrvMsg
import io.jobial.scase.core.MessageReceiveResult
import io.jobial.scase.core.MessageSendResult
import io.jobial.scase.core.RequestHandler
import io.jobial.scase.core.SendResponseResult
import io.jobial.scase.core.impl.CatsUtils
import io.jobial.scase.core.impl.ConsumerProducerService
import io.jobial.scase.core.impl.DefaultMessageSendResult
import io.jobial.scase.core.impl.DefaultService
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshaller
import io.jobial.scase.marshalling.Unmarshaller


class TibrvRequestResponseService[F[_] : Concurrent, REQ, RESP: Marshaller](
  val requestConsumer: TibrvConsumer[F, REQ],
  val requestHandler: RequestHandler[F, REQ, RESP]
)(
  implicit val requestUnmarshaller: Unmarshaller[REQ],
  responseMarshaller: Marshaller[Either[Throwable, RESP]],
  val context: TibrvContext
) extends DefaultService[F] with ConsumerProducerService[F, REQ, RESP] with TibrvSupport {

  val defaultProducerId = None

  val autoCommitRequest = true

  val autoCommitFailedRequest = true

  def sendResult(request: MessageReceiveResult[F, REQ], responseDeferred: Deferred[F, SendResponseResult[RESP]]): F[MessageSendResult[F, _]] =
    for {
      listener <- request.underlyingContext[TibrvListener]
      tibrvMsgRequest <- request.underlyingMessage[TibrvMsg]
      response <- responseDeferred.get
      tibrvMsgResponse = new TibrvMsg(Marshaller[Either[Throwable, RESP]].marshal(response.response))
      _ <- delay(listener.getTransport.sendReply(tibrvMsgResponse, tibrvMsgRequest))
    } yield new DefaultMessageSendResult[F, RESP](unit, unit)
}

object TibrvRequestResponseService extends CatsUtils with Logging {

  def apply[F[_] : Concurrent, REQ, RESP: Marshaller](
    requestConsumer: TibrvConsumer[F, REQ],
    requestHandler: RequestHandler[F, REQ, RESP]
  )(
    implicit requestUnmarshaller: Unmarshaller[REQ],
    responseMarshaller: Marshaller[Either[Throwable, RESP]],
    context: TibrvContext
  ) = delay(new TibrvRequestResponseService(requestConsumer, requestHandler))
}