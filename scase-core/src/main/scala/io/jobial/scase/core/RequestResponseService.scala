package io.jobial.scase.core

import cats.MonadError
import cats.effect.Concurrent

import scala.concurrent.duration._
import scala.util.Try

case class RequestWrapper[REQ](
  payload: REQ,
  correlationId: String,
  responseConsumerId: String,
  requestTimeout: Duration
)

// Error handling and correlation is part of the protocol, a wrapper is needed to allow meta information
case class ResponseWrapper[RESP](
  payload: Try[RESP],
  correlationId: String
)

trait SendResponseResult[+RESP]

trait RequestContext[F[_]] {

  def reply[REQUEST, RESPONSE](request: REQUEST, response: RESPONSE)
    (implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE]): F[SendResponseResult[RESPONSE]]

  def requestTimeout: Duration
}


/**
 * Request[RESPONSE] cannot be covariant. This is easy to see because to enforce the response type, reply(...) must
 * have an argument type of RESPONSE which would be impossible with a covariant type parameter 
 * (if it was covariant, it would be possible to assign a Request[SUBTYPE1] to a Request[SUPERTYPE] and then call reply with 
 * a SUBTYPE2 as the response, which would break type safety). The consequence of the invariance is that:
 *
 * 1) reply(...) is type safe, which is good;
 * 2) It is not possible to declare RequestResponseClient as RequestResponseClient[REQ <: Request[RESP], RESP] because
 * that would assume covariance;
 * 3) It is not possible to use something like Request[MyResponseType] as the common supertype for requests because that would
 * assume covariance again - the solution is to use another supertype - e.g. declare a separate 
 * sealed trait MyRequestType to enforce type safety on the client and the consumers / producers - this is not a huge
 * constraint though.
 *
 * This is all the consequence of the requirement to enforce type safety on the response for each request and also
 * the fact that we want a common reply(...) method declared in the Request trait. The other option would be 
 * the Akka typed route, which is basically making a reply sender reference part of the request message. The downside of that
 * is:
 *
 * 1) A sender reference needs to be declared explicitly in every request message, making it part of the model;
 * 2) The ask syntax is complicated and unintuitive - see https://doc.akka.io/docs/akka/current/typed/interaction-patterns.html#request-response;
 * 3) Requires the existence of sender references with marshalling - this is fine in Akka where
 * serializable references are assumed to be used everywhere, but it is quite intrusive if the application model is forced to have
 * these references everywhere. I think extending the trait is less intrusive and less verbose.
 *
 * Of course, the choice of adding Request[RESPONSE] does not preclude the addition of an Akka-style API at some point
 * if extending the trait will ever become a constraint.
 *
 */
trait Request[RESPONSE]

// The actual service logic is separated from the message producer and consumers in the RequestProcessor
trait RequestProcessor[F[_], REQ, RESP] {

  type Processor = Function[REQ, F[SendResponseResult[RESP]]]

  def processor(f: Processor): Processor = f

  def responseTransformer(f: F[SendResponseResult[RESP]] => F[SendResponseResult[RESP]]) = f

  case class UnknownRequest(request: REQ) extends IllegalStateException

  /**
   * Reply is sent through the sender to enforce the response type specific for the request. 
   * Return type is SendResponseResult to make sure a reply has been sent by processRequest. 
   */
  def processRequestOrFail(implicit context: RequestContext[F], me: MonadError[F, Throwable]): Function[REQ, F[SendResponseResult[RESP]]] =
    processRequest
  //  orElse {
  //      case request =>
  //        MonadError[F, Throwable].raiseError(UnknownRequest(request))
  //    }

  def processRequest(implicit context: RequestContext[F]): Processor

  def afterResponse(request: REQ): PartialFunction[Try[SendResponseResult[RESP]], Unit] = {
    case _ =>
  }

  /**
   * Indicates that the requests are intended to be processed sequentially by this request processor.
   * It is up to the service implementation to enforce this. The request processor can still use concurrency (e.g. Futures),
   * setting this field to true only guarantees that each future returned by the processor are waited before it starts
   * processing a new request. This functionality is useful in cases where the service needs to use an external resource
   * that can only be used exclusively (e.g. a browser).
   */
  def sequentialRequestProcessing = false

  def sequentialRequestProcessingTimeout = 1.hour

}

/**
 * Configuration for a service. The service definition can be used to create or deploy the service as well as
 * creating a client for it. The service definition can be shared between the server and client side.
 */
trait RequestResponseServiceConfiguration[REQ, RESP] {

  def serviceName: String
}

trait RemoteRequestResponseServiceConfiguration[REQ, RESP] extends RequestResponseServiceConfiguration[REQ, RESP] {

  def service[F[_] : Concurrent](requestProcessor: RequestProcessor[F, REQ, RESP]): F[RequestResponseService[F, REQ, RESP]]

  def client[F[_] : Concurrent]: F[RequestResponseClient[F, REQ, RESP]]
}

trait RequestResponseService[F[_], REQ, RESP] {

  def startService: F[RequestResponseServiceState[F, REQ]]
}

trait RequestResponseServiceState[F[_], REQ] {

  def stopService: F[RequestResponseServiceState[F, REQ]]
}

