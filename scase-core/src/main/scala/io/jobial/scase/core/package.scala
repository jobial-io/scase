package io.jobial.scase

import cats.Monad
import io.jobial.scase.core.RequestResult
import io.jobial.scase.logging.Logging


package object core extends Logging {
  implicit def requestResultToResult[F[_], RESPONSE](requestResult: RequestResult[F, RESPONSE])(implicit m: Monad[F]) =
    Monad[F].map(requestResult.response)(_.message)

  implicit def sendRequest[F[_], REQUEST, RESPONSE](request: REQUEST)(
    implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE],
    client: RequestResponseClient[F, _ >: REQUEST, _ >: RESPONSE], sendRequestContext: SendRequestContext, m: Monad[F]): F[RESPONSE] =
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

  implicit class reqRespClientExtension[F[_], REQ, RESP](client: RequestResponseClient[F, REQ, RESP]) {

    def sendRequest[REQUEST <: REQ, RESPONSE <: RESP](request: REQUEST with Request[RESPONSE])(implicit requestResponseMapping: RequestResponseMapping[REQUEST with Request[RESPONSE], RESPONSE], sendRequestContext: SendRequestContext): RequestResult[F, RESPONSE] =
      client.sendRequest(request)(requestResponseMapping, sendRequestContext)
  }
}
