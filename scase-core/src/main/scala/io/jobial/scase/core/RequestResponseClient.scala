package io.jobial.scase.core

import scala.concurrent.duration.Duration


trait RequestResponseResult[F[_], REQUEST, RESPONSE] {

  def request: MessageSendResult[F, REQUEST]

  def response: MessageReceiveResult[F, RESPONSE]
}

case class SendRequestContext(
  requestTimeout: Option[Duration] = None,
  attributes: Map[String, String] = Map()
)

trait RequestResponseClient[F[_], REQ, RESP] {

  // TODO: revisit this, should the mapping be implicit?
  def sendRequestWithResponseMapping[REQUEST <: REQ, RESPONSE <: RESP](request: REQUEST, requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE])
    (implicit sendRequestContext: SendRequestContext): F[RequestResponseResult[F, REQUEST, RESPONSE]]

  def stop: F[Unit]
}

case class RequestTimeout(timeout: Duration, cause: Throwable)
  extends IllegalStateException(s"request timed out after $timeout", cause)

object RequestTimeout {
  
  def apply(timeout: Duration): RequestTimeout = RequestTimeout(timeout, null)
}
