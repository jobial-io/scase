package io.jobial.scase.core

import cats.effect.IO

import scala.annotation.implicitNotFound
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag


trait RequestResult[RESPONSE] {

  def response: IO[MessageReceiveResult[RESPONSE]]

  def commit: IO[_]
}

case class SendRequestContext(
  requestTimeout: Duration,
  attributes: Map[String, String] = Map()
)

@implicitNotFound("No mapping found from request type ${REQUEST} to response type ${RESPONSE}")
trait RequestResponseMapping[REQUEST, RESPONSE]

trait RequestResponseClient[REQ, RESP] {

  def sendRequest[REQUEST <: REQ, RESPONSE <: RESP](request: REQUEST)
    (implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE], sendRequestContext: SendRequestContext): RequestResult[RESPONSE]

  def ?[REQUEST <: REQ, RESPONSE <: RESP : ClassTag](request: REQUEST)
    (implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE], sendRequestContext: SendRequestContext) =
    sendRequest(request).response.map(_.message)
}
