package io.jobial.scase.core

import cats.effect.Concurrent
import cats.{Monad, MonadError}

import java.util.concurrent.LinkedBlockingQueue
import cats.effect.concurrent.Deferred
import cats.implicits._
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

case class ConsumerProducerRequestResponseServiceState[F[_] : Monad, REQ, RESP](
  subscription: MessageSubscription[F, REQ],
  service: ConsumerProducerRequestResponseService[F, REQ, RESP]
) extends RequestResponseServiceState[F, REQ]
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

case class ConsumerProducerRequestResponseService[F[_] : Concurrent, REQ: Unmarshaller, RESP: Marshaller](
  messageConsumer: MessageConsumer[F, REQ],
  messageProducer: String => F[MessageProducer[F, Either[Throwable, RESP]]], // TODO: add support for fixed producer case
  requestProcessor: RequestProcessor[F, REQ, RESP],
  messageProducerForErrors: Option[MessageProducer[F, REQ]] = None, // TODO: implement this
  autoCommitRequest: Boolean = true,
  autoCommitFailedRequest: Boolean = true
)(
  implicit responseMarshallable: Marshaller[Either[Throwable, RESP]]
  //sourceContext: SourceContext
) extends RequestResponseService[F, REQ, RESP] with Logging {

  private def handleRequest(request: MessageReceiveResult[F, REQ]) = {
    logger.info(s"received request in service: ${request.toString.take(500)}")
    val r: F[SendResponseResult[RESP]] =
      request.responseConsumerId match {
        case Some(responseConsumerId) =>
          logger.info(s"found response consumer id $responseConsumerId in ")

          (for {
            producer <- messageProducer(responseConsumerId)
            response <- Deferred[F, Either[Throwable, RESP]]
            processorResult <- {
              logger.debug(s"found response producer $producer for request in service: ${request.toString.take(500)}")
              // TODO: make this a Deferred

              val processorResult =
                requestProcessor.processRequestOrFail(new RequestContext[F] {

                  def reply[REQUEST, RESPONSE](req: REQUEST, r: RESPONSE)
                    (implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE]): F[SendResponseResult[RESPONSE]] = {
                    logger.debug(s"context sending response ${r.toString.take(500)}")
                    val re = r.asInstanceOf[RESP]

                    for {
                      _ <- response.complete(Right(re))
                    } yield new SendResponseResult[RESPONSE] {}
                  }

                  val requestTimeout = request.requestTimeout.getOrElse(Duration.Inf)

                }, Concurrent[F])(request.message)

              val processResultWithErrorHandling = processorResult.map(Right[Throwable, SendResponseResult[RESP]](_): Either[Throwable, SendResponseResult[RESP]]).handleErrorWith { case t =>
                logger.error(s"request processing failed: ${request.toString.take(500)}", t)
                val x = for {
                  _ <- response.complete(Left(t))
                } yield (Left(t): Either[Throwable, SendResponseResult[RESP]])
                x
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
                x <- response.get
                y <- x match {
                  case Right(r) =>
                    //println("sending")
                    logger.debug(s"sending success to client for request: ${request.toString.take(500)} on $producer")
                    val sendResult = producer.send(Right(r), responseAttributes)
                    // commit request after result is written
                    if (autoCommitRequest) {
                      request.commit()
                      logger.debug(s"service committed request: ${request.toString.take(500)} on $producer")
                    }
                    sendResult
                  case Left(t) =>
                    //println("failure")
                    logger.error(s"sending failure to client for request: ${request.toString.take(500)}", t)
                    for {
                      _ <- producer.send(Left(t), responseAttributes)
                    } yield
                      if (autoCommitFailedRequest) {
                        request.commit()
                        logger.debug(s"service committed request: ${request.toString.take(500)}")
                      }
                }
              } yield {
                //println("finished sending reply")
                r match {
                  case Right(r) =>
                    Monad[F].pure(r)
                  case Left(t) =>
                    // TODO: revisit this...
                    Monad[F].pure(new SendResponseResult[RESP] {})
                }
              }
            }
          } yield processorResult).flatten
        case None =>
          logger.error(s"response consumer id not found for request: ${request.toString.take(500)}")
          MonadError[F, Throwable].raiseError[SendResponseResult[RESP]](ResponseConsumerIdNotFound())
      }

    r
  }

  def start = {
    logger.info(s"starting service for processor $requestProcessor")

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
      logger.info(s"started service for processor $requestProcessor")
      ConsumerProducerRequestResponseServiceState(subscription, this)
    }
  }

  def startAndJoin: F[RequestResponseServiceState[F, REQ]] =
    for {
      state <- start
      result <- state.join
    } yield result
    
}

case class ResponseConsumerIdNotFound() extends IllegalStateException