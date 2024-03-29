/*
 * Copyright (c) 2020 Jobial OÜ. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is located at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package io.jobial.scase.aws.lambda

import cats.effect.Concurrent
import cats.effect.Deferred
import cats.implicits._
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import io.jobial.scase.core.DefaultMessageReceiveResult
import io.jobial.scase.core.MessageReceiveResult
import io.jobial.scase.core.SendMessageContext
import io.jobial.scase.core.impl.CatsUtils
import io.jobial.scase.core.impl.DefaultSendResponseResult
import io.jobial.scase.core.RequestContext
import io.jobial.scase.core.RequestHandler
import io.jobial.scase.core.RequestResponseMapping
import io.jobial.scase.core.SendResponseResult
import io.jobial.scase.core.impl.ConcurrentEffect
import io.jobial.scase.logging.Logging
import org.apache.commons.io.IOUtils
import org.joda.time.DateTime
import java.io.InputStream
import java.io.OutputStream
import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.DurationInt

abstract class LambdaRequestHandler[F[_], REQ, RESP]
  extends RequestStreamHandler
    with RequestHandler[F, REQ, RESP]
    with CatsUtils
    with Logging {

  def serviceConfiguration: LambdaServiceConfiguration[REQ, RESP]

  implicit def concurrent: ConcurrentEffect[F]

  def disableRetry = true

  val awsRequestIdCache = TrieMap[String, String]()

  override def handleRequest(inputStream: InputStream, outputStream: OutputStream, context: Context) = {
    val awsRequestId = context.getAwsRequestId
    if (disableRetry && awsRequestIdCache.put(awsRequestId, awsRequestId).isDefined) {
      logger.warn(s"Already invoked with request Id $awsRequestId, not retrying.")
    } else {
      val requestString = IOUtils.toString(inputStream, "utf-8")

      val result =
        for {
          _ <- trace(s"received request: ${requestString.take(500)}")
          request <- Concurrent[F].fromEither(serviceConfiguration.requestUnmarshaller.unmarshalFromText(requestString))
          responseDeferred <- Deferred[F, Either[Throwable, RESP]]
          processorResult: F[SendResponseResult[RESP]] =
            handleRequest(new RequestContext[F] {

              // TODO: revisit this
              val requestTimeout = 15.minutes

              override def reply[REQUEST, RESPONSE](request: REQUEST, response: Either[Throwable, RESPONSE])(
                implicit requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE],
                sendMessageContext: SendMessageContext
              ) = pure(DefaultSendResponseResult[RESPONSE](response, sendMessageContext))

              override def receiveResult[REQUEST](request: REQUEST): MessageReceiveResult[F, REQUEST] =
                DefaultMessageReceiveResult(pure(request), context.getClientContext.getEnvironment.asScala.toMap, None, unit, unit,
                  pure(requestString), pure(context))

            })(request)
          // TODO: use redeem when Cats is upgraded, 2.0.0 simply doesn't support mapping errors to an F[B]...
          _ <- processorResult
            .flatMap { result =>
              responseDeferred.complete(result.response) >> unit
            }
            .handleError { t =>
              error(s"request processing failed: $request", t) >>
                responseDeferred.complete(Left(t)) >> unit
            }
          // send response when ready
          r <- responseDeferred.get
        } yield
          r match {
            case Right(r) =>
              logger.trace(s"sending success to client for request: $request")
              outputStream.write(serviceConfiguration.responseMarshaller.marshalToText(r).getBytes("utf-8"))
            case Left(t) =>
              logger.trace(s"sending failure to client for request: $request", t)
              throw t
          }

      runResult(result)
    }
  }

  def runResult(result: F[_]): Unit

}

trait LambdaScheduledRequestHandler[F[_], REQ, RESP] extends LambdaRequestHandler[F, REQ, RESP] {
  this: RequestHandler[F, REQ, RESP] =>

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
