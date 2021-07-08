package io.jobial.scase

import cats.Monad
import cats.effect.IO
import io.jobial.scase.logging.Logging

import java.util.concurrent._
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag


package object core extends Logging {
  implicit lazy val executionContext = ExecutionContextWithShutdown(Executors.newCachedThreadPool)

  implicit def requestResultToResult[F[_], RESPONSE](requestResult: RequestResult[F, RESPONSE])(implicit m: Monad[F]) =
    Monad[F].map(requestResult.response)(_.message)

  implicit def sendRequest[F[_], REQUEST, RESPONSE](request: REQUEST)(
    implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE],
    client: RequestResponseClient[F, _ >: REQUEST, _ >: RESPONSE], sendRequestContext: SendRequestContext, m: Monad[F]): F[RESPONSE] =
    client.?(request)

  val CorrelationIdKey = "CorrelationId"

  val ResponseConsumerIdKey = "ResponseConsumerId"

  val RequestTimeoutKey = "RequestTimeout"

  val clientExecutionContext = executionContext

  val serviceExecutionContext = executionContext

  val forwarderExecutionContext = executionContext

  implicit class RequestExtension[F[_], REQUEST](request: REQUEST) {

    /**
     * Syntactic sugar to allow the syntax request.reply(...).
     */
    def reply[RESPONSE](response: RESPONSE)(implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE], context: RequestContext[F]) =
      context.reply(request, response)
  }

  implicit def requestTagBasedRequestResponseMapping[REQUEST <: Request[RESPONSE], RESPONSE] =
    new RequestResponseMapping[REQUEST, RESPONSE] {}
}
