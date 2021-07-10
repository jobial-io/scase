package io.jobial.scase

import cats.Monad
import io.jobial.scase.logging.Logging
import shapeless._
import cats.implicits._

package object core extends Logging {
  
  implicit def requestResultToResponse[F[_] : Monad, RESPONSE](requestResult: RequestResult[F, RESPONSE]) =
    requestResult.response.map(_.message)

  implicit def sendRequest[F[_], REQUEST, RESPONSE](request: REQUEST)(
    implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE],
    client: RequestResponseClient[F, _ >: REQUEST, _ >: RESPONSE], sendRequestContext: SendRequestContext, M: Monad[F]): F[RESPONSE] =
    client.?(request)

  val CorrelationIdKey = "CorrelationId"

  val ResponseConsumerIdKey = "ResponseConsumerId"

  val RequestTimeoutKey = "RequestTimeout"

  implicit class RequestExtension[F[_], REQUEST](request: REQUEST) {

    /**
     * Syntactic sugar to allow the syntax request.reply(...).
     */
    def reply[RESPONSE](response: RESPONSE)(implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE], context: RequestContext[F]) =
      context.reply(request, response)
  }

  implicit def requestTagBasedRequestResponseMapping[REQUEST <: Request[RESPONSE], RESPONSE] =
    new RequestResponseMapping[REQUEST, RESPONSE] {}

  //  implicit def requestTagBasedRequestResponseMapping[REQUEST] =
  //    new RequestResponseMapping[REQUEST, RESPONSE] {}

  implicit class requestResponseClientExtension[F[_], REQ, RESP](client: RequestResponseClient[F, REQ, RESP])(implicit x: <:<[REQ, Request[_ <: RESP]]) {

    def sendRequest[REQUEST <: REQ, RESPONSE <: RESP](request: REQUEST with Request[RESPONSE])(implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE], sendRequestContext: SendRequestContext): RequestResult[F, RESPONSE] =
      client.sendRequestWithResponseMapping(request, requestResponseMapping)

    def ?[REQUEST <: REQ, RESPONSE <: RESP](request: REQUEST with Request[RESPONSE])
      (implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE], sendRequestContext: SendRequestContext, m: Monad[F]) =
      Monad[F].map(sendRequest(request).response)(_.message)
  }

  implicit class requestTagBasedRequestResponseClientExtension[F[_], REQ, RESP](client: RequestResponseClient[F, REQ, RESP])(implicit x: <:!<[REQ, Request[_ <: RESP]]) {

    def sendRequest[REQUEST <: REQ, RESPONSE <: RESP](request: REQUEST)(implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE], sendRequestContext: SendRequestContext): RequestResult[F, RESPONSE] =
      client.sendRequestWithResponseMapping(request, requestResponseMapping)

    def ?[REQUEST <: REQ, RESPONSE <: RESP](request: REQUEST)
      (implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE], sendRequestContext: SendRequestContext, m: Monad[F]) =
      Monad[F].map(sendRequest(request).response)(_.message)
  }

}
