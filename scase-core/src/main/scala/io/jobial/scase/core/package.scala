package io.jobial.scase

import cats.effect.IO
import io.jobial.scase.logging.Logging

import java.util.concurrent._
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag


package object core extends Logging {
  implicit lazy val executionContext = ExecutionContextWithShutdown(Executors.newCachedThreadPool)

  implicit def requestResultToResult[RESPONSE](requestResult: RequestResult[RESPONSE]) =
    requestResult.response.map(_.message)

  implicit def sendRequest[REQ, RESP, REQUEST <: REQ, RESPONSE <: RESP : ClassTag](request: REQUEST)(
    implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE], client: RequestResponseClient[_ >: REQ, _ >: RESP], sendRequestContext: SendRequestContext): IO[RESPONSE] =
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

  implicit class RequestExtension[REQUEST](request: REQUEST) {

    /**
     * Syntactic sugar to allow the syntax request.reply(...).
     */
    def reply[RESPONSE](response: RESPONSE)(implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE], context: RequestContext) =
      context.reply(request, response)
  }

  implicit def requestTagBasedRequestResponseMapping[REQUEST <: Request[RESPONSE], RESPONSE] =
    new RequestResponseMapping[REQUEST, RESPONSE] {}
}
