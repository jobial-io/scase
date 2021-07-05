package io.jobial.scase.aws.lambda

import java.io.{InputStream, OutputStream}

import cats.effect.{ContextShift, IO}
import cats.effect.concurrent.Deferred
import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import io.jobial.scase.core.{RequestContext, RequestProcessor, RequestResponseMapping, SendResponseResult}
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}
import org.apache.commons.io.IOUtils
import org.joda.time.DateTime

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.DurationInt

trait LambdaRequestHandler[REQ, RESP] extends RequestStreamHandler with Logging {
  this: RequestProcessor[REQ, RESP] =>

  def requestUnmarshaller: Unmarshaller[REQ]

  def responseMarshaller: Marshaller[RESP]
  
  implicit def cs: ContextShift[IO]

  def disableRetry = true

  val awsRequestIdCache = TrieMap[String, String]()

  override def handleRequest(inputStream: InputStream, outputStream: OutputStream, context: Context) = {
    val awsRequestId = context.getAwsRequestId
    if (disableRetry && awsRequestIdCache.put(awsRequestId, awsRequestId).isDefined) {
      logger.warn(s"Already invoked with request Id $awsRequestId, not retrying.")
    } else {
      val requestString = IOUtils.toString(inputStream, "utf-8")
      val request = requestUnmarshaller.unmarshalFromText(requestString)

      logger.info(s"received request: ${requestString.take(500)}")

      val result =
        for {
          responseDeferred <- Deferred[IO, Either[Throwable, RESP]]
          r <- responseDeferred.get
          processorResult: IO[SendResponseResult[RESP]] =
          processRequestOrFail(new RequestContext {

            // TODO: revisit this
            val requestTimeout = 15.minutes

            override def reply[REQUEST, RESPONSE](request: REQUEST, response: RESPONSE)
              (implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE]): IO[SendResponseResult[RESPONSE]] =
              for {
                _ <- responseDeferred.complete(Right(r.asInstanceOf[RESP]))
              } yield new SendResponseResult[RESPONSE] {}

          })(request)
          _ <- processorResult.handleErrorWith { t =>
            logger.error(s"request processing failed: $request", t)
            responseDeferred.complete(Left(t))
          }
        } yield
          // send response when ready
          r match {
            case Right(r) =>
              logger.debug(s"sending success to client for request: $request")
              //          val sendResult = consumer.send(, request.message.correlationId))
              outputStream.write(responseMarshaller.marshalToText(r).getBytes("utf-8"))
            case Left(t) =>
              logger.error(s"sending failure to client for request: $request", t)
              //outputStream.write(implicitly[JsonWriter[Try[RESP]]].write(Failure(t)).compactPrint.getBytes("utf-8"))
              throw t
          }


      result.unsafeRunTimed(15.minutes)
    }
  }
}

trait LambdaScheduledRequestHandler[REQ, RESP] extends LambdaRequestHandler[REQ, RESP] {
  this: RequestProcessor[REQ, RESP] =>

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