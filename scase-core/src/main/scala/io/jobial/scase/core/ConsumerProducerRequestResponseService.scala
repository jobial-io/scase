package io.jobial.scase.core

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import cats.effect.IO
import cats.implicits._
import cats.effect.concurrent.Deferred
import io.jobial.scase.future.futureWithTimeout
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}
import io.jobial.scase.monitoring.SourceContext

import scala.concurrent.Await.result
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.Future.failed
import scala.concurrent.Future.successful
import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.Failure
import scala.util.Success
import scala.util.Try

case class ConsumerProducerRequestResponseServiceState[REQ, RESP](
  subscription: MessageSubscription[REQ],
  service: ConsumerProducerRequestResponseService[REQ, RESP]
) extends RequestResponseServiceState[REQ]
  with Logging {

  def stopService: IO[RequestResponseServiceState[REQ]] =
    for {
      r <- subscription.cancel
      _ <- subscription.subscription
    } yield {
      logger.info(s"Shutting down $service")
      //service.executionContext.shutdown
      this
    }
}

case class ConsumerProducerRequestResponseService[REQ: Unmarshaller, RESP: Marshaller](
  messageConsumer: MessageConsumer[REQ],
  messageProducer: String => IO[MessageProducer[Either[Throwable, RESP]]], // TODO: add support for fixed producer case
  requestProcessor: RequestProcessor[REQ, RESP],
  messageProducerForErrors: Option[MessageProducer[REQ]] = None, // TODO: implement this
  autoCommitRequest: Boolean = true,
  autoCommitFailedRequest: Boolean = true
)(
  implicit responseMarshallable: Marshaller[Either[Throwable, RESP]]
  //sourceContext: SourceContext
) extends RequestResponseService[REQ, RESP] with Logging {

  //logger.error(s"created ConsumerProducerRequestResponseService", new RuntimeException)
  implicit val executionContext = serviceExecutionContext

  private def handleRequest(request: MessageReceiveResult[REQ]) = {
    logger.info(s"received request in service: ${request.toString.take(500)}")
    val r: IO[SendResponseResult[RESP]] =
      request.responseConsumerId match {
        case Some(responseConsumerId) =>

          implicit val cs = IO.contextShift(ExecutionContext.global)

          for {
            producer <- messageProducer(responseConsumerId)
            response <- Deferred[IO, Try[RESP]]
            processorResult <- {
              logger.debug(s"found response producer $producer for request in service: ${request.toString.take(500)}")
              // TODO: make this a Deferred

              val processorResult =
                requestProcessor.processRequestOrFail(new RequestContext {

                  def reply[REQUEST, RESPONSE](req: REQUEST, r: RESPONSE)
                    (implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE]): IO[SendResponseResult[RESPONSE]] =  {
                    logger.debug(s"context sending response ${r.toString.take(500)}")
                    val re = r.asInstanceOf[RESP]

                    println("setting reply")
                    for {
                      _ <- response.complete(Success(re))
                    } yield new SendResponseResult[RESPONSE] {}
                  }

                  val requestTimeout = request.requestTimeout.getOrElse(Duration.Inf)

                })(request.message)

              val processResultWithErrorHandling = processorResult.handleErrorWith { case t =>
                logger.error(s"request processing failed: ${request.toString.take(500)}", t)
                response.complete(Failure(t))
                IO.raiseError(t)
              }

              // handle processor timeout
              //              futureWithTimeout(response.future, request.requestTimeout.getOrElse(10.minutes)) recover { case t =>
              //                logger.error(s"request processing exceeded the request timeout: $request")
              //                // TODO: fail response? interrupt processor?
              //              }

              val responseAttributes = request.correlationId.map(correlationId => Map(CorrelationIdKey -> correlationId)).getOrElse(Map())

              // send response when ready
              for {
                r <- processorResult
                x <- response.get
                y <- x match {
                  case Success(r) =>
                    println("sending")
                    logger.debug(s"sending success to client for request: ${request.toString.take(500)} on $producer")
                    val sendResult = producer.send(Right(r), responseAttributes)
                    // commit request after result is written
                    if (autoCommitRequest) {
                      request.commit()
                      logger.debug(s"service committed request: ${request.toString.take(500)} on $producer")
                    }
                    sendResult
                  case Failure(t) =>
                    println("failure")
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
                println("finished sending reply")
                r
              }
            }
          } yield processorResult
        case None =>
          logger.error(s"response consumer id not found for request: ${request.toString.take(500)}")
          IO.raiseError[SendResponseResult[RESP]](ResponseConsumerIdNotFound())
      }

    // TODO: implement this with IO: .andThen(requestProcessor.afterResponse(request.message))

    r
  }

  def startService = {
    logger.info(s"starting service for processor $requestProcessor")

    val requestQueue = new LinkedBlockingQueue[() => Future[SendResponseResult[RESP]]]

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


}

case class ResponseConsumerIdNotFound() extends IllegalStateException