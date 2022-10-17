package io.jobial.scase

import cats.Monad
import cats.implicits._
import shapeless._

package object core {

  implicit class RequestExtension[F[_], REQUEST](request: REQUEST) {

    /**
     * Syntactic sugar to allow the syntax request.reply(...). This version allows sending an explicit error.
     */
    def reply[RESPONSE](response: Either[Throwable, RESPONSE])(implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE], context: RequestContext[F], sendMessageContext: SendMessageContext): F[SendResponseResult[RESPONSE]] =
      context.reply(request, response)

    /**
     * Syntactic sugar to allow the syntax request.reply(...).
     */
    def reply[RESPONSE](response: RESPONSE)(implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE], context: RequestContext[F], sendMessageContext: SendMessageContext = SendMessageContext()): F[SendResponseResult[RESPONSE]] =
      reply(Right(response))

    /**
     * Syntactic sugar to allow the syntax request ! response.
     */
    def ![RESPONSE](response: RESPONSE)(implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE], context: RequestContext[F], sendMessageContext: SendMessageContext = SendMessageContext()): F[SendResponseResult[RESPONSE]] =
      reply(response)

    def underlyingMessage[T](implicit context: RequestContext[F]) =
      context.receiveResult(request).underlyingMessage[T]

    def underlyingContext[T](implicit context: RequestContext[F]) =
      context.receiveResult(request).underlyingContext[T]

    def attributes(implicit context: RequestContext[F]) =
      context.receiveResult(request).attributes
  }

  implicit def requestResultToResponse[F[_] : Monad, REQUEST, RESPONSE](requestResult: RequestResponseResult[F, REQUEST, RESPONSE]) =
    requestResult.response.message

  implicit def sendRequest[F[_], REQUEST, RESPONSE](request: REQUEST)(
    implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE],
    client: RequestResponseClient[F, _ >: REQUEST, _ >: RESPONSE], sendRequestContext: SendRequestContext = SendRequestContext(), M: Monad[F]): F[RESPONSE] =
    client.?(request)

  implicit def requestTagBasedRequestResponseMapping[REQUEST <: Request[_ <: RESPONSE], RESPONSE] =
    new RequestResponseMapping[REQUEST, RESPONSE] {}

  implicit class requestResponseClientExtension[F[_], REQ, RESP](client: RequestResponseClient[F, REQ, RESP])(implicit x: <:<[REQ, Request[_ <: RESP]]) {

    def sendRequest[REQUEST <: REQ, RESPONSE <: RESP](request: REQUEST with Request[RESPONSE])(implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE], sendRequestContext: SendRequestContext = SendRequestContext()): F[RequestResponseResult[F, REQUEST, RESPONSE]] =
      client.sendRequestWithResponseMapping(request, requestResponseMapping)

    def ?[REQUEST <: REQ, RESPONSE <: RESP](request: REQUEST with Request[RESPONSE])
      (implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE], sendRequestContext: SendRequestContext = SendRequestContext(), m: Monad[F]) =
      for {
        sendResult <- sendRequest(request)
        message <- sendResult.response.message
      } yield message
  }

  implicit class requestTagBasedRequestResponseClientExtension[F[_], REQ, RESP](client: RequestResponseClient[F, REQ, RESP])(implicit x: <:!<[REQ, Request[_ <: RESP]]) {

    def sendRequest[REQUEST <: REQ, RESPONSE <: RESP](request: REQUEST)(implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE], sendRequestContext: SendRequestContext = SendRequestContext()): F[RequestResponseResult[F, REQUEST, RESPONSE]] =
      client.sendRequestWithResponseMapping(request, requestResponseMapping)

    def ?[REQUEST <: REQ, RESPONSE <: RESP](request: REQUEST)
      (implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE], sendRequestContext: SendRequestContext = SendRequestContext(), m: Monad[F]) =
      for {
        sendResult <- sendRequest(request)
        message <- sendResult.response.message
      } yield message
  }

  implicit class MessageExtension[F[_], M](message: M) {
    
    def underlyingMessage[T](implicit context: MessageContext[F]) =
      context.receiveResult(message).underlyingMessage[T]

    def underlyingContext[T](implicit context: MessageContext[F]) =
      context.receiveResult(message).underlyingContext[T]

    def attributes(implicit context: MessageContext[F]) =
      context.receiveResult(message).attributes
  }
  
  val CorrelationIdKey = "CorrelationId"

  val ResponseProducerIdKey = "ResponseProducerId"

  val RequestTimeoutKey = "RequestTimeout"

}
