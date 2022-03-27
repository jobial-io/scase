package io.jobial.scase.core.impl

import cats.Monad
import cats.effect.Concurrent
import cats.effect.concurrent.Ref
import cats.implicits._
import io.jobial.scase.core.ReceiverClient
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
) extends Logging {


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
                        // TODO: add attributes
                        implicit val sendMessageContext = SendMessageContext()
                        for {
                          response <- filteredResponse.message
                        } yield request ! response
                      case None =>
                        Concurrent[F].raiseError[SendResponseResult[SOURCERESP]](new RuntimeException)
                    }
                  } yield sendResult
                case None =>
                  Concurrent[F].raiseError(new RuntimeException)
              }
            } yield sendResult
        }
      })
      r <- service.start
    } yield r

  def stop = stopped.set(true)
}

