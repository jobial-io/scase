package io.jobial.scase.core.impl

import cats.effect.Concurrent
import cats.effect.concurrent.Ref
import cats.implicits._
import io.jobial.scase.core._
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshaller
import io.jobial.scase.marshalling.Unmarshaller

class RequestResponseBridge[F[_] : Concurrent, SOURCEREQ: Unmarshaller, SOURCERESP: Marshaller, DESTREQ: Unmarshaller, DESTRESP: Marshaller](
  source: RequestHandler[F, SOURCEREQ, SOURCERESP] => F[Service[F]],
  destination: MessageReceiveResult[F, DESTREQ] => F[RequestResponseResult[F, DESTREQ, DESTRESP]],
  filterRequest: MessageReceiveResult[F, SOURCEREQ] => F[Option[MessageReceiveResult[F, DESTREQ]]],
  filterResponse: (MessageReceiveResult[F, SOURCEREQ], RequestResponseResult[F, DESTREQ, DESTRESP]) => F[Option[MessageReceiveResult[F, SOURCERESP]]],
  stopped: Ref[F, Boolean]
)(
  implicit requestResponseMapping: RequestResponseMapping[SOURCEREQ, SOURCERESP]
) extends CatsUtils with Logging {

  // TODO: return state here instead?
  def start =
    for {
      service <- source(new RequestHandler[F, SOURCEREQ, SOURCERESP] {
        override def handleRequest(implicit context: RequestContext[F]) = {
          case request: SOURCEREQ =>
            val sourceResult = context.receiveResult(request)
            for {
              filteredRequest <- filterRequest(sourceResult)
              sendResult <- filteredRequest match {
                case Some(filteredRequest) =>
                  for {
                    destinationResult <- destination(filteredRequest)
                    filteredResponse <- filterResponse(sourceResult, destinationResult)
                    sendResult <- filteredResponse match {
                      case Some(filteredResponse) =>
                        implicit val sendMessageContext = SendMessageContext(
                          filteredResponse.attributes ++ sourceResult.attributes.get(CorrelationIdKey).map(correlationId => CorrelationIdKey -> correlationId)
                        )
                        for {
                          response <- filteredResponse.message
                          r <- request ! response
                        } yield r
                      case None =>
                        raiseError[F, SendResponseResult[SOURCERESP]](new RuntimeException)
                    }
                  } yield sendResult
                case None =>
                  raiseError(new RuntimeException)
              }
            } yield sendResult
        }
      })
      handler <- service.start
    } yield handler

  def stop = stopped.set(true)
}

object RequestResponseBridge extends CatsUtils with Logging {

  def apply[F[_] : Concurrent, SOURCEREQ: Unmarshaller, SOURCERESP: Marshaller, DESTREQ: Unmarshaller, DESTRESP: Marshaller](
    source: RequestHandler[F, SOURCEREQ, SOURCERESP] => F[Service[F]],
    destination: MessageReceiveResult[F, DESTREQ] => F[RequestResponseResult[F, DESTREQ, DESTRESP]],
    filterRequest: MessageReceiveResult[F, SOURCEREQ] => F[Option[MessageReceiveResult[F, DESTREQ]]],
    filterResponse: (MessageReceiveResult[F, SOURCEREQ], RequestResponseResult[F, DESTREQ, DESTRESP]) => F[Option[MessageReceiveResult[F, SOURCERESP]]]
  )(
    implicit requestResponseMapping: RequestResponseMapping[SOURCEREQ, SOURCERESP]
  ) = for {
    stopped <- Ref.of[F, Boolean](false)
  } yield new RequestResponseBridge[F, SOURCEREQ, SOURCERESP, DESTREQ, DESTRESP](
    source,
    destination,
    filterRequest,
    filterResponse,
    stopped
  )

  def apply[F[_] : Concurrent, REQ: Unmarshaller, RESP: Marshaller](
    source: RequestHandler[F, REQ, RESP] => F[Service[F]],
    destination: RequestResponseClient[F, REQ, RESP]
  )(
    implicit requestResponseMapping: RequestResponseMapping[REQ, RESP]
  ) = for {
    stopped <- Ref.of[F, Boolean](false)
  } yield new RequestResponseBridge[F, REQ, RESP, REQ, RESP](
    source,
    { request =>
      for {
        destRequest <- request.message
        r <- destination.sendRequest(destRequest)
      } yield r
    },
    { result => pure(Some(result)) },
    { (_, result) => pure(Some(result.response)) },
    stopped
  )

}