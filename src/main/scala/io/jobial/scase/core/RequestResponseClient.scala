package io.jobial.scase.core

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag


trait RequestResult[RESPONSE] {

  def response: Future[MessageReceiveResult[RESPONSE]]

  def commit: Future[_]
}

case class SendRequestContext(
  requestTimeout: Duration,
  attributes: Map[String, String] = Map()
)

trait RequestResponseClient[REQ, RESP] {

  def sendRequest[REQUEST <: REQ, RESPONSE <: RESP](request: REQUEST with Request[RESPONSE])
    (implicit sendRequestContext: SendRequestContext): RequestResult[RESPONSE]

  def ?[REQUEST <: REQ, RESPONSE <: RESP : ClassTag](request: REQUEST with Request[RESPONSE])
    (implicit sendRequestContext: SendRequestContext) =
    sendRequest(request).response.map(_.message)
}
