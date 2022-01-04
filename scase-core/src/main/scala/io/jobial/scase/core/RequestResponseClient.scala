package io.jobial.scase.core

import scala.concurrent.duration.Duration


trait RequestResult[F[_], RESPONSE] {

  def response: F[MessageReceiveResult[F, RESPONSE]]

  def commit: F[Unit]
}

case class SendRequestContext(
  requestTimeout: Option[Duration] = None,
  attributes: Map[String, String] = Map()
)

trait RequestResponseClient[F[_], REQ, RESP] {
  
  def sendRequestWithResponseMapping[REQUEST <: REQ, RESPONSE <: RESP](request: REQUEST, requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE])
    (implicit sendRequestContext: SendRequestContext): RequestResult[F, RESPONSE]
}
