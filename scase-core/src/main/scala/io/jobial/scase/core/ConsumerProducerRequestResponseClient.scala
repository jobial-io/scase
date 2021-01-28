package io.jobial.scase.core

import java.util.UUID.randomUUID
import cats.effect.{ContextShift, IO}
import io.jobial.scase.future.scheduledFuture
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}
import io.jobial.scase.monitoring.MonitoringPublisher
import io.jobial.scase.monitoring.noPublisher

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.concurrent.duration._

case class ConsumerProducerRequestResponseClient[REQ: Marshaller, RESP](
  messageConsumer: MessageConsumer[Either[Throwable, RESP]],
  messageProducer: () => MessageProducer[REQ],
  responseConsumerId: String,
  autoCommitResponse: Boolean = true,
  name: String = randomUUID.toString
)(
  //implicit executionContext: ExecutionContext,
  implicit responseMarshallable: Unmarshaller[Either[Throwable, RESP]],
  monitoringPublisher: MonitoringPublisher = noPublisher
) extends RequestResponseClient[REQ, RESP] with Logging {

  val holdOntoOutstandingRequest = true

  case class CorrelationInfo(responsePromise: Promise[MessageReceiveResult[RESP]], sendTime: Long, request: Option[REQ])

  val correlations = TrieMap[String, CorrelationInfo]()

  //logger.debug(s"client expecting responses on ${messageConsumer}")

  def logOutsanding =
    if (correlations.size > 0)
      logger.warn(s"Outstanding correlations: ${correlations} on ${this}")

  def scheduleLogOutstanding: Future[_] =
    scheduledFuture(3 seconds) {
      logOutsanding
      scheduleLogOutstanding
    }

  scheduleLogOutstanding

  val subscription = messageConsumer.subscribe { response =>
    IO {
      logger.debug(s"received response ${response.toString.take(500)}")

      response.correlationId match {
        case Some(correlationId) =>
          correlations.get(correlationId) match {
            case Some(correlationInfo) =>
              response.message match {
                case Right(payload) =>
                  logger.debug(s"client received success: ${response.toString.take(500)}")
                  correlationInfo.responsePromise.success(
                    MessageReceiveResult(
                      payload,
                      response.attributes,
                      {
                        response.commit
                        // TODO: add removal
                      },
                      {
                        response.rollback
                        // TODO: add removal
                      }
                    )
                  )
                  for {
                    request <- correlationInfo.request
                  } yield
                    monitoringPublisher.timing(request.getClass.getName, correlationInfo.sendTime)
                case Left(t) =>
                  logger.error(s"client received failure: ${response.toString.take(500)}", t)
                  correlationInfo.responsePromise.failure(t)
              }

              correlations.remove(correlationId)

              if (autoCommitResponse) {
                response.commit()
                logger.debug(s"client committed response ${response.toString.take(500)}")
              }
            case None =>
              logger.error(s"${System.identityHashCode(this)} received message that cannot be correlated to a request: ${response.toString.take(500)}")
              if (autoCommitResponse)
                response.commit()
          }
        case None =>
          logger.error(s"${System.identityHashCode(this)} received message without correlation id: ${response.toString.take(500)}")
          if (autoCommitResponse)
            response.commit()
      }
    }
  }

  case class ConsumerProducerRequestResult[RESPONSE <: RESP](response: IO[MessageReceiveResult[RESPONSE]]) extends RequestResult[RESPONSE] {
    def commit = response.flatMap(_.commit())
  }

  def sendRequest[REQUEST <: REQ, RESPONSE <: RESP](request: REQUEST)
    (implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE], sendRequestContext: SendRequestContext) =
    ConsumerProducerRequestResult {

      val producer = messageProducer()
      // TODO: this seems to be critical line for initialization, figure out why...
      //logger.debug(s"client sending requests to ${producer}")
      //logger.debug(s"client sending requests")
      //messageProducer.toString
      //println(sendRequestContext)
      val promise = Promise[MessageReceiveResult[RESP]]()
      val correlationId = randomUUID.toString

      monitoringPublisher.increment(request.getClass.getName)
      logger.info(s"sending ${request.toString.take(500)} with $correlationId using $this")

      correlations.put(correlationId, CorrelationInfo(
        promise,
        System.currentTimeMillis,
        if (holdOntoOutstandingRequest)
          Some(request)
        else
          None
      ))

      implicit val cs = IO.contextShift(ExecutionContext.global)

      for {
        sendResult <- producer.send(
          request,
          Map(
            CorrelationIdKey -> correlationId,
            ResponseConsumerIdKey -> responseConsumerId,
            RequestTimeoutKey -> sendRequestContext.requestTimeout.toMillis.toString
          )
        )
        receiveResult <- IO.fromFuture(IO(promise.future))
      } yield
        // TODO: revisit this - maybe Marshallable[RESPONSE] should be an implicit
        receiveResult.asInstanceOf[MessageReceiveResult[RESPONSE]]
    }

}
