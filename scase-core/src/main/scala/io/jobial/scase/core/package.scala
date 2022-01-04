package io.jobial.scase

import cats.Monad
import cats.effect.IO
import io.jobial.scase.logging.Logging
import shapeless._
import cats.implicits._

package object core extends Logging {

  implicit class RequestExtension[F[_], REQUEST](request: REQUEST) {

    /**
     * Syntactic sugar to allow the syntax request.reply(...).
     */
    def reply[RESPONSE](response: RESPONSE)(implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE], context: RequestContext[F]) =
      context.reply(request, response)

    /**
     * Syntactic sugar to allow the syntax request ! response.
     */
    def ![RESPONSE](response: RESPONSE)(implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE], context: RequestContext[F]) =
      reply(response)
  }

  implicit def requestResultToResponse[F[_] : Monad, RESPONSE](requestResult: RequestResult[F, RESPONSE]) =
    requestResult.response.map(_.message)

  implicit def sendRequest[F[_], REQUEST, RESPONSE](request: REQUEST)(
    implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE],
    client: RequestResponseClient[F, _ >: REQUEST, _ >: RESPONSE], sendRequestContext: SendRequestContext = SendRequestContext(), M: Monad[F]): F[RESPONSE] =
    client.?(request)

  implicit def requestTagBasedRequestResponseMapping[REQUEST <: Request[RESPONSE], RESPONSE] =
    new RequestResponseMapping[REQUEST, RESPONSE] {}

  implicit class requestResponseClientExtension[F[_], REQ, RESP](client: RequestResponseClient[F, REQ, RESP])(implicit x: <:<[REQ, Request[_ <: RESP]]) {

    def sendRequest[REQUEST <: REQ, RESPONSE <: RESP](request: REQUEST with Request[RESPONSE])(implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE], sendRequestContext: SendRequestContext = SendRequestContext()): RequestResult[F, RESPONSE] =
      client.sendRequestWithResponseMapping(request, requestResponseMapping)

    def ?[REQUEST <: REQ, RESPONSE <: RESP](request: REQUEST with Request[RESPONSE])
      (implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE], sendRequestContext: SendRequestContext = SendRequestContext(), m: Monad[F]) =
      Monad[F].map(sendRequest(request).response)(_.message)
  }

  implicit class requestTagBasedRequestResponseClientExtension[F[_], REQ, RESP](client: RequestResponseClient[F, REQ, RESP])(implicit x: <:!<[REQ, Request[_ <: RESP]]) {

    def sendRequest[REQUEST <: REQ, RESPONSE <: RESP](request: REQUEST)(implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE], sendRequestContext: SendRequestContext = SendRequestContext()): RequestResult[F, RESPONSE] =
      client.sendRequestWithResponseMapping(request, requestResponseMapping)

    def ?[REQUEST <: REQ, RESPONSE <: RESP](request: REQUEST)
      (implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE], sendRequestContext: SendRequestContext = SendRequestContext(), m: Monad[F]) =
      Monad[F].map(sendRequest(request).response)(_.message)
  }

  implicit def sendResponseResultToIO[T](result: SendResponseResult[T]): IO[SendResponseResult[T]] = IO(result)

  val CorrelationIdKey = "CorrelationId"

  val ResponseConsumerIdKey = "ResponseConsumerId"

  val RequestTimeoutKey = "RequestTimeout"

}
