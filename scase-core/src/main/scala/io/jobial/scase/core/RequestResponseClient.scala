package io.jobial.scase.core

import cats.Monad

import scala.annotation.implicitNotFound
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag


trait RequestResult[F[_], RESPONSE] {

  def response: F[MessageReceiveResult[F, RESPONSE]]

  def commit: F[_]
}

case class SendRequestContext(
  requestTimeout: Duration,
  attributes: Map[String, String] = Map()
)

@implicitNotFound("No mapping found from request type ${REQUEST} to response type ${RESPONSE}")
trait RequestResponseMapping[REQUEST, RESPONSE]

trait RequestResponseClient[F[_], REQ, RESP] {
  
  def sendRequest[REQUEST <: REQ, RESPONSE <: RESP](request: REQUEST, requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE])
    (implicit sendRequestContext: SendRequestContext): RequestResult[F, RESPONSE]

//  def sendRequest[REQUEST <: REQ, RESPONSE <: RESP](request: REQUEST with Request[RESPONSE])
//    (implicit sendRequestContext: SendRequestContext): RequestResult[F, RESPONSE] =
//    sendRequest(request)(new RequestResponseMapping[REQUEST with Request[RESPONSE], RESPONSE] {}, sendRequestContext)
}
