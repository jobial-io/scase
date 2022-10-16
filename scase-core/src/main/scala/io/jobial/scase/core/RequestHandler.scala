package io.jobial.scase.core

import scala.concurrent.duration._

trait SendResponseResult[+RESP] {

  def response: RESP
}

trait RequestContext[F[_]] {

  def reply[REQUEST, RESPONSE](request: REQUEST, response: RESPONSE)
    (implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE], sendMessageContext: SendMessageContext = SendMessageContext()): F[SendResponseResult[RESPONSE]]

  def receiveResult[REQUEST](request: REQUEST): MessageReceiveResult[F, REQUEST]

  def requestTimeout: Duration
}

trait RequestHandler[F[_], REQ, RESP] {

  type Handler = Function[REQ, F[SendResponseResult[RESP]]]

  def handleRequest(implicit context: RequestContext[F]): Handler

}

object RequestHandler {

  def apply[F[_], REQ, RESP](handler: RequestContext[F] => Function[REQ, F[SendResponseResult[RESP]]]) =
    new RequestHandler[F, REQ, RESP] {
      def handleRequest(implicit context: RequestContext[F]): Handler = handler(context)
    }
}

case class UnknownRequest[REQ](request: REQ) extends IllegalStateException


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