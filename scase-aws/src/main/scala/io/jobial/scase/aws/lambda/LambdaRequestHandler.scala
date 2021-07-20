package io.jobial.scase.aws.lambda

import cats.effect.Concurrent
import cats.effect.concurrent.Deferred
import cats.implicits._
import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import io.jobial.scase.core.{RequestContext, RequestProcessor, RequestResponseMapping, SendResponseResult}
import io.jobial.scase.logging.Logging
import org.apache.commons.io.IOUtils
import org.joda.time.DateTime

import java.io.{InputStream, OutputStream}
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.DurationInt

abstract class LambdaRequestHandler[F[_], REQ, RESP](val serviceConfiguration: LambdaRequestResponseServiceConfiguration[REQ, RESP]) extends RequestStreamHandler with Logging {
  this: RequestProcessor[F, REQ, RESP] =>

  implicit def concurrent: Concurrent[F]

  def disableRetry = true

  val awsRequestIdCache = TrieMap[String, String]()

  override def handleRequest(inputStream: InputStream, outputStream: OutputStream, context: Context) = {
    val awsRequestId = context.getAwsRequestId
    if (disableRetry && awsRequestIdCache.put(awsRequestId, awsRequestId).isDefined) {
      logger.warn(s"Already invoked with request Id $awsRequestId, not retrying.")
    } else {
      val requestString = IOUtils.toString(inputStream, "utf-8")
      

      logger.info(s"received request: ${requestString.take(500)}")

      val result =
        for {
          request <- serviceConfiguration.requestUnmarshaller.unmarshalFromText(requestString).to[F]
          responseDeferred <- Deferred[F, Either[Throwable, RESP]]
          r <- responseDeferred.get
          processorResult: F[SendResponseResult[RESP]] =
            processRequestOrFail(new RequestContext[F] {

              // TODO: revisit this
              val requestTimeout = 15.minutes

              override def reply[REQUEST, RESPONSE](request: REQUEST, response: RESPONSE)
                (implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE]): F[SendResponseResult[RESPONSE]] =
                for {
                  _ <- responseDeferred.complete(Right(r.asInstanceOf[RESP]))
                } yield new SendResponseResult[RESPONSE] {}

            }, concurrent)(request)
          // TODO: use redeem when Cats is upgraded, 2.0.0 simply doesn't support mapping errors to an F[B]...
          _ <- processorResult.handleError { t =>
            logger.error(s"request processing failed: $request", t)
            responseDeferred.complete(Left(t))
            new SendResponseResult[RESP] {}
          }
        } yield
          // send response when ready
          r match {
            case Right(r) =>
              logger.debug(s"sending success to client for request: $request")
              //          val sendResult = consumer.send(, request.message.correlationId))
              outputStream.write(serviceConfiguration.responseMarshaller.marshalToText(r).getBytes("utf-8"))
            case Left(t) =>
              logger.error(s"sending failure to client for request: $request", t)
              //outputStream.write(implicitly[JsonWriter[Try[RESP]]].write(Failure(t)).compactPrint.getBytes("utf-8"))
              throw t
          }


      runResult(result)
    }
  }

  def runResult(result: F[_]): Unit =
  // TODO: result.unsafeRunTimed(15.minutes) for IO, for example
    ???

}

trait LambdaScheduledRequestHandler[F[_], REQ, RESP] extends LambdaRequestHandler[F, REQ, RESP] {
  this: RequestProcessor[F, REQ, RESP] =>

  def mapScheduledEvent(event: CloudWatchEvent): REQ
}

case class CloudWatchEvent(
  id: String,
  `detail-type`: String,
  source: String,
  account: String,
  time: DateTime,
  region: String,
  resources: Seq[String]
  //detail: JsObject
)