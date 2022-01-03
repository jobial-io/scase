package io.jobial.scase.core.impl

import cats.effect.Concurrent
import cats.effect.concurrent.Deferred
import cats.implicits._
import cats.{Monad, MonadError}
import io.jobial.scase.core.{CorrelationIdKey, MessageConsumer, MessageProducer, MessageReceiveResult, MessageSendResult, MessageSubscription, RequestContext, RequestHandler, RequestResponseMapping, SendResponseResult, Service, ServiceState}
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}

import scala.concurrent.duration.Duration

case class DefaultServiceState[F[_] : Monad, M](
  subscription: MessageSubscription[F, M],
  service: Service[F]
) extends ServiceState[F]
  with Logging {

  def stop =
    for {
      r <- subscription.cancel
      _ <- subscription.join
    } yield {
      logger.info(s"Shutting down $service")
      //service.executionContext.shutdown
      this
    }

  def join =
    for {
      _ <- subscription.join
    } yield {
      this
    }
}

case class DefaultSendResponseResult[RESPONSE](response: RESPONSE) extends SendResponseResult[RESPONSE]

case class ConsumerProducerRequestResponseService[F[_] : Concurrent, REQ: Unmarshaller, RESP: Marshaller](
  messageConsumer: MessageConsumer[F, REQ],
  messageProducer: String => F[MessageProducer[F, Either[Throwable, RESP]]], // TODO: add support for fixed producer case
  requestHandler: RequestHandler[F, REQ, RESP],
  messageProducerForErrors: Option[MessageProducer[F, REQ]] = None, // TODO: implement this
  autoCommitRequest: Boolean = true,
  autoCommitFailedRequest: Boolean = true
)(
  implicit responseMarshallable: Marshaller[Either[Throwable, RESP]]
  //sourceContext: SourceContext
) extends DefaultService[F] with Logging {

  private def handleRequest(request: MessageReceiveResult[F, REQ]) = {
    logger.info(s"received request in service: ${request.toString.take(500)}")
    val r: F[MessageSendResult[Either[Throwable, RESP]]] =
      request.responseConsumerId match {
        case Some(responseConsumerId) =>
          logger.info(s"found response consumer id $responseConsumerId in ")

          for {
            producer <- messageProducer(responseConsumerId)
            response <- Deferred[F, Either[Throwable, RESP]]
            processorResult <- {
              logger.debug(s"found response producer $producer for request in service: ${request.toString.take(500)}")
              // TODO: make this a Deferred

              val processorResult =
                requestHandler.handleRequestOrFail(new RequestContext[F] {

                  def reply[REQUEST, RESPONSE](req: REQUEST, r: RESPONSE)
                    (implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE]): SendResponseResult[RESPONSE] = {
                    logger.debug(s"context sending response ${r.toString.take(500)}")
                    val re = r.asInstanceOf[RESP]

                    //                    for {
                    //                      _ <- response.complete(Right(re))
                    //                    } yield new 
                    DefaultSendResponseResult[RESPONSE](r)
                  }

                  val requestTimeout = request.requestTimeout.getOrElse(Duration.Inf)

                }, Concurrent[F])(request.message)

              val processResultWithErrorHandling = processorResult
                .flatMap { result =>
                  response.complete(Right(result.response))
                }
                .handleErrorWith { case t =>
                  logger.error(s"request processing failed: ${request.toString.take(500)}", t)
                  response.complete(Left(t))
                }

              // handle processor timeout
              //              futureWithTimeout(response.future, request.requestTimeout.getOrElse(10.minutes)) recover { case t =>
              //                logger.error(s"request processing exceeded the request timeout: $request")
              //                // TODO: fail response? interrupt processor?
              //              }

              val responseAttributes = request.correlationId.map(correlationId => Map(CorrelationIdKey -> correlationId)).getOrElse(Map())

              // send response when ready
              for {
                r <- processResultWithErrorHandling
                res <- response.get
                resultAfterSend <- res match {
                  case Right(r) =>
                    logger.debug(s"sending success to client for request: ${request.toString.take(500)} on $producer")
                    for {
                      sendResult <- producer.send(Right(r), responseAttributes)
                      // commit request after result is written
                      _ <- if (autoCommitRequest) {
                        logger.debug(s"service committing request: ${request.toString.take(500)} on $producer")
                        request.commit()
                      } else Monad[F].unit
                    } yield sendResult
                  case Left(t) =>
                    logger.error(s"sending failure to client for request: ${request.toString.take(500)}", t)
                    for {
                      sendResult <- producer.send(Left(t), responseAttributes)
                      _ <- if (autoCommitFailedRequest) {
                        logger.debug(s"service committing request: ${request.toString.take(500)}")
                        request.commit()
                      } else Monad[F].unit
                    } yield sendResult
                }
              } yield resultAfterSend
             }
          } yield processorResult
        case None =>
          logger.error(s"response consumer id not found for request: ${request.toString.take(500)}")
          MonadError[F, Throwable].raiseError(ResponseConsumerIdNotFound())
      }

    r
  }

  def start = {
    logger.info(s"starting service for processor $requestHandler")

    //val requestQueue = new LinkedBlockingQueue[() => Future[SendResponseResult[RESP]]]

    //    val state: ConsumerProducerRequestResponseServiceState[REQ, RESP] = 
    for {
      //        if (requestProcessor.sequentialRequestProcessing) {
      //          // add requests to a queue and process then sequentially
      //
      //          val subscription = messageConsumer.subscribe { request: MessageReceiveResult[REQ] =>
      //            requestQueue.add({ () =>
      //              handleRequest(request).unsafeToFuture()
      //            })
      //          }
      //
      //          def pollRequestQueue: Future[_] =
      //            Future {
      //              // TODO: make this recursive to yield (trampolining)
      //              logger.info(s"polling request queue in $this")
      //              if (!subscription.isCancelled) {
      //                Option(requestQueue.poll(10, TimeUnit.MILLISECONDS)).map { r =>
      //                  logger.debug(s"got request from request queue in $this")
      //                  result(r(), requestProcessor.sequentialRequestProcessingTimeout)
      //                }
      //              }
      //            }.flatMap(_ => pollRequestQueue)
      //          // TODO: this could be generalized into a sequential queue or something which could be used as the messageProducer
      //          //  - or as a wrapper around it
      //
      //          pollRequestQueue
      //
      //          subscription
      //        } else
      // process requests asynchronously
      subscription <- messageConsumer.subscribe(handleRequest)
    } yield {
      logger.info(s"started service for processor $requestHandler")
      DefaultServiceState(subscription, this)
    }
  }
  
}

case class ResponseConsumerIdNotFound() extends IllegalStateException