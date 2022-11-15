package io.jobial.scase.core.impl

import cats.effect.Ref
import cats.effect.Sync
import cats.implicits._
import io.jobial.scase.core._
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshaller
import io.jobial.scase.marshalling.Unmarshaller
import scala.concurrent.duration.FiniteDuration

class RequestResponseBridge[F[_] : ConcurrentEffect, SOURCEREQ: Unmarshaller, SOURCERESP: Marshaller, DESTREQ: Unmarshaller, DESTRESP: Marshaller](
  source: RequestHandler[F, SOURCEREQ, SOURCERESP] => F[Service[F]],
  destination: MessageReceiveResult[F, DESTREQ] => F[Option[RequestResponseResult[F, DESTREQ, DESTRESP]]],
  filterRequest: MessageReceiveResult[F, SOURCEREQ] => F[Option[MessageReceiveResult[F, DESTREQ]]],
  filterResponse: (MessageReceiveResult[F, SOURCEREQ], RequestResponseResult[F, DESTREQ, DESTRESP]) => F[Option[MessageReceiveResult[F, SOURCERESP]]],
  stopped: Ref[F, Boolean]
)(
  implicit requestResponseMapping: RequestResponseMapping[SOURCEREQ, SOURCERESP]
) extends DefaultService[F] with CatsUtils with Logging {

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
                    _ <- trace(s"received result from destination: $destinationResult")
                    filteredResponse <- {
                      for {
                        destinationResult <- destinationResult
                      } yield for {
                        filteredResponse <-
                          for {
                            filteredResponse <- filterResponse(sourceResult, destinationResult)
                          } yield filteredResponse
                      } yield filteredResponse
                    }.sequence
                    sendResult <- filteredResponse.flatten match {
                      case Some(filteredResponse) =>
                        implicit val sendMessageContext = SendMessageContext(
                          filteredResponse.attributes ++ sourceResult.attributes.get(CorrelationIdKey).map(correlationId => CorrelationIdKey -> correlationId)
                        )
                        for {
                          response <- filteredResponse.message
                          r <- request ! response
                        } yield r
                      case None =>
                        trace(s"no destination for request: ${sourceResult}") >>
                          raiseError[F, SendResponseResult[SOURCERESP]](new IllegalStateException)
                    }
                  } yield sendResult
                case None =>
                  trace(s"not forwarding request: ${sourceResult}") >>
                    raiseError[F, SendResponseResult[SOURCERESP]](new IllegalStateException)
              }
            } yield sendResult
        }
      })
      handler <- service.start
    } yield new RequestResponseBridgeServiceState[F](this, service) {
      def stop = handler.stop >> pure(this)

      def join: F[ServiceState[F]] =
        handler.join >> pure(this)

    }
}

abstract class RequestResponseBridgeServiceState[F[_] : Sync](
  val service: Service[F],
  val requestResponseService: Service[F]
) extends ServiceState[F]

object RequestResponseBridge extends CatsUtils with Logging {

  def apply[F[_] : ConcurrentEffect, SOURCEREQ: Unmarshaller, SOURCERESP: Marshaller, DESTREQ: Unmarshaller, DESTRESP: Marshaller](
    source: RequestHandler[F, SOURCEREQ, SOURCERESP] => F[Service[F]],
    destination: MessageReceiveResult[F, DESTREQ] => F[Option[RequestResponseResult[F, DESTREQ, DESTRESP]]],
    filterRequest: MessageReceiveResult[F, SOURCEREQ] => F[Option[MessageReceiveResult[F, DESTREQ]]],
    filterResponse: (MessageReceiveResult[F, SOURCEREQ], RequestResponseResult[F, DESTREQ, DESTRESP]) => F[Option[MessageReceiveResult[F, SOURCERESP]]]
  )(
    implicit requestResponseMapping: RequestResponseMapping[SOURCEREQ, SOURCERESP]
  ): F[RequestResponseBridge[F, SOURCEREQ, SOURCERESP, DESTREQ, DESTRESP]] =
    for {
      stopped <- Ref.of[F, Boolean](false)
    } yield new RequestResponseBridge[F, SOURCEREQ, SOURCERESP, DESTREQ, DESTRESP](
      source,
      destination,
      filterRequest,
      filterResponse,
      stopped
    )

  def apply[F[_] : ConcurrentEffect, REQ: Unmarshaller, RESP: Marshaller](
    source: RequestHandler[F, REQ, RESP] => F[Service[F]],
    destination: MessageReceiveResult[F, REQ] => F[Option[RequestResponseResult[F, REQ, RESP]]],
    filterRequest: MessageReceiveResult[F, REQ] => F[Option[MessageReceiveResult[F, REQ]]]
  )(
    implicit requestResponseMapping: RequestResponseMapping[REQ, RESP]
  ): F[RequestResponseBridge[F, REQ, RESP, REQ, RESP]] =
    RequestResponseBridge[F, REQ, RESP, REQ, RESP](
      source,
      destination,
      filterRequest,
      { (_, result) => pure(Some(result.response)) }
    )

  def fixedDestination[F[_] : ConcurrentEffect, REQ, RESP](destination: RequestResponseClient[F, REQ, RESP])(implicit requestResponseMapping: RequestResponseMapping[REQ, RESP]) = { r: MessageReceiveResult[F, REQ] =>
    for {
      message <- r.message
      sendResult <- destination.sendRequestWithResponseMapping(message, requestResponseMapping)(SendRequestContext(r.requestTimeout, r.attributes - ResponseProducerIdKey - ResponseTopicKey - CorrelationIdKey))
    } yield Option(sendResult)
  }

  def destinationBasedOnSourceRequest[F[_] : ConcurrentEffect, REQ, RESP](destination: MessageReceiveResult[F, REQ] => F[Option[RequestResponseClient[F, REQ, RESP]]], timeout: FiniteDuration)(implicit requestResponseMapping: RequestResponseMapping[REQ, RESP]) = { r: MessageReceiveResult[F, REQ] =>
    for {
      message <- r.message
      requestTimeout = r.requestTimeout.getOrElse(timeout)
      d <- destination(r)
      sendResult <- d.map(d => d.sendRequestWithResponseMapping(message, requestResponseMapping)(SendRequestContext(Some(requestTimeout), r.attributes - ResponseProducerIdKey - ResponseTopicKey - CorrelationIdKey))).sequence
    } yield sendResult
  }

  def allowAllFilter[F[_] : ConcurrentEffect, M] = { r: MessageReceiveResult[F, M] =>
    pure(Option(r))
  }

  def requestResponseOnlyFilter[F[_] : ConcurrentEffect, SOURCEREQ]: MessageReceiveResult[F, SOURCEREQ] => F[Option[MessageReceiveResult[F, SOURCEREQ]]] = { r: MessageReceiveResult[F, SOURCEREQ] =>
    if (r.responseProducerId.isDefined)
      pure(Option(r))
    else
      pure(None)
  }
}