package io.jobial.scase

import cats.Monad
import cats.effect.IO
import io.jobial.scase.logging.Logging

import java.util.concurrent._
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag


package object core extends Logging {
  implicit lazy val executionContext = ExecutionContextWithShutdown(Executors.newCachedThreadPool)

  implicit def requestResultToResult[F[_], RESPONSE](requestResult: RequestResult[F, RESPONSE])(implicit m: Monad[F]) =
    Monad[F].map(requestResult.response)(_.message)

  implicit def sendRequest[REQ1, RESP1, REQUEST1 <: REQ1, RESPONSE1 <: RESP1: ClassTag](request: REQUEST1)(
    implicit requestResponseMapping: RequestResponseMapping[REQUEST1, RESPONSE1],
    client: RequestResponseClient[IO, _ >: REQ1, _ >: RESP1], sendRequestContext: SendRequestContext): IO[RESPONSE1] =
    client.?(request)

  /**
   * This executor is deliberately not a synchronous executor but a dedicated single threaded pool. A synchronous executor
   * is inherently non-deterministic because it depends on the caller thread (which can be a thread in a multi-threaded
   * pool, which would break the single threaded behaviour).
   */
  object SingleThreadedExecutionContext {
    implicit lazy val instance = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))
  }

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
