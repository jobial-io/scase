package io.jobial.scase.core

import scala.concurrent.TimeoutException
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

  def sendRequestWithResponseMapping[REQUEST <: REQ, RESPONSE <: RESP](request: REQUEST, requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE])
    (implicit sendRequestContext: SendRequestContext): F[RequestResponseResult[F, REQUEST, RESPONSE]]

  def stop: F[Unit]
}

case class RequestTimeout[F[_]](client: RequestResponseClient[F, _, _], timeout: Duration)
  extends TimeoutException(s"request timed out in $client after $timeout")
