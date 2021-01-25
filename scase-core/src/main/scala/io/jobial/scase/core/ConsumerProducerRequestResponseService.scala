package io.jobial.scase.core

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import cats.effect.IO
import io.jobial.scase.future.futureWithTimeout
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshallable
import io.jobial.scase.monitoring.SourceContext

import scala.concurrent.Await.result
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.Future.failed
import scala.concurrent.Future.successful
import scala.concurrent.duration.Duration
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

case class ConsumerProducerRequestResponseService[REQ: Marshallable, RESP](
  messageConsumer: MessageConsumer[REQ],
  messageProducer: String => IO[MessageProducer[Try[RESP]]], // TODO: add support for fixed producer case
  requestProcessor: RequestProcessor[REQ, RESP],
  messageProducerForErrors: Option[MessageProducer[REQ]] = None, // TODO: implement this
  autoCommitRequest: Boolean = true,
  autoCommitFailedRequest: Boolean = true
)(
  implicit responseMarshallable: Marshallable[Try[RESP]],
  sourceContext: SourceContext
) extends RequestResponseService[REQ, RESP] with Logging {

  //logger.error(s"created ConsumerProducerRequestResponseService", new RuntimeException)
  implicit val executionContext = serviceExecutionContext

  private def handleRequest(request: MessageReceiveResult[REQ]) = {
    logger.info(s"received request in service: ${request.toString.take(500)}")
    val r: IO[SendResponseResult[RESP]] = (
      request.responseConsumerId match {
        case Some(responseConsumerId) =>

          implicit val cs = IO.contextShift(ExecutionContext.global)

          IO.fromFuture(IO(for {
            producer <- messageProducer(responseConsumerId)
          } yield {
            logger.debug(s"found response producer $producer for request in service: ${request.toString.take(500)}")
            val response = Promise[RESP]

            val processorResult = IO {
              requestProcessor.processRequestOrFail(new RequestContext {

                def reply[REQUEST, RESPONSE](req: REQUEST, r: RESPONSE)
                  (implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE]): SendResponseResult[RESPONSE] = {
                  logger.debug(s"context sending response ${r.toString.take(500)}")
                  val re = r.asInstanceOf[RESP]
                  response.complete(Success(re))
                  new SendResponseResult[RESPONSE] {}
                }

                val requestTimeout = request.requestTimeout.getOrElse(Duration.Inf)

              })(request.message)
            }.flatMap(identity).unsafeToFuture

            processorResult.recoverWith { case t =>
              logger.error(s"request processing failed: ${request.toString.take(500)}", t)
              response.complete(Failure(t))
              failed(t)
            }

            // handle processor timeout
            futureWithTimeout(response.future, request.requestTimeout.getOrElse(Duration.Inf)) recover { case t =>
              logger.error(s"request processing exceeded the request timeout: $request")
              // TODO: fail response? interrupt processor?
            }

            val responseAttributes = request.correlationId.map(correlationId => Map(CorrelationIdKey -> correlationId)).getOrElse(Map())

            // send response when ready
            response.future andThen {
              case Success(r) =>
                logger.debug(s"sending success to client for request: ${request.toString.take(500)} on $producer")
                val sendResult = producer.send(Success(r), responseAttributes)
                // commit request after result is written
                if (autoCommitRequest) {
                  request.commit()
                  logger.debug(s"service committed request: ${request.toString.take(500)} on $producer")
                }
                sendResult
              case Failure(t) =>
                logger.error(s"sending failure to client for request: ${request.toString.take(500)}", t)
                for {
                  _ <- producer.send(Failure(t), responseAttributes)
                } yield
                  if (autoCommitFailedRequest) {
                    request.commit()
                    logger.debug(s"service committed request: ${request.toString.take(500)}")
                  }
            }

            processorResult
          }).flatMap(identity))
        case None =>
          logger.error(s"response consumer id not found for request: ${request.toString.take(500)}")
          IO.raiseError[SendResponseResult[RESP]](ResponseConsumerIdNotFound())
      }
      )
      // TODO: implement this with IO: .andThen(requestProcessor.afterResponse(request.message))

    r
  }

  def startService = IO {
    logger.info(s"starting service for processor $requestProcessor")

    val requestQueue = new LinkedBlockingQueue[() => Future[SendResponseResult[RESP]]]

    val state: ConsumerProducerRequestResponseServiceState[REQ, RESP] = ConsumerProducerRequestResponseServiceState(
      {
        if (requestProcessor.sequentialRequestProcessing) {
          // add requests to a queue and process then sequentially

          val subscription = messageConsumer.subscribe { request: MessageReceiveResult[REQ] =>
            requestQueue.add({ () =>
              handleRequest(request).unsafeToFuture()
            })
          }

          def pollRequestQueue: Future[_] =
            Future {
              // TODO: make this recursive to yield (trampolining)
              logger.info(s"polling request queue in $this")
              if (!subscription.isCancelled) {
                Option(requestQueue.poll(10, TimeUnit.MILLISECONDS)).map { r =>
                  logger.debug(s"got request from request queue in $this")
                  result(r(), requestProcessor.sequentialRequestProcessingTimeout)
                }
              }
            }.flatMap(_ => pollRequestQueue)
          // TODO: this could be generalized into a sequential queue or something which could be used as the messageProducer
          //  - or as a wrapper around it

          pollRequestQueue

          subscription
        } else
        // process requests asynchronously
          messageConsumer.subscribe(handleRequest)
      },
      this
    )

    logger.info(s"started service for processor $requestProcessor")
    state
  }


}

case class ResponseConsumerIdNotFound() extends IllegalStateException