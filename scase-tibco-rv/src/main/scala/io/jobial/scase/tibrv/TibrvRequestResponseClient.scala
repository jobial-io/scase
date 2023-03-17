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
package io.jobial.scase.tibrv

import cats.effect.Concurrent
import cats.implicits._
import com.tibco.tibrv.TibrvMsg
import io.jobial.scase.core.DefaultMessageReceiveResult
import io.jobial.scase.core.RequestResponseClient
import io.jobial.scase.core.RequestResponseMapping
import io.jobial.scase.core.RequestResponseResult
import io.jobial.scase.core.RequestTimeout
import io.jobial.scase.core.SendRequestContext
import io.jobial.scase.core.impl.CatsUtils
import io.jobial.scase.core.impl.DefaultMessageSendResult
import io.jobial.scase.core.impl.DefaultRequestResponseResult
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshaller
import io.jobial.scase.marshalling.Unmarshaller

import java.time.Instant.now
import scala.concurrent.duration._

class TibrvRequestResponseClient[F[_] : Concurrent, REQ: Marshaller, RESP](
  val subject: String
)(
  implicit val context: TibrvContext,
  responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
) extends RequestResponseClient[F, REQ, RESP] with TibrvSupport with CatsUtils with Logging {

  lazy val transport = createTransport

  override def sendRequestWithResponseMapping[REQUEST <: REQ, RESPONSE <: RESP](
    request: REQUEST,
    requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE]
  )(
    implicit sendRequestContext: SendRequestContext
  ): F[RequestResponseResult[F, REQUEST, RESPONSE]] =
    for {
      _ <- delay(initRv)
      tibrvMsg = new TibrvMsg(Marshaller[REQ].marshal(request))
      _ = tibrvMsg.setSendSubject(subject)
      timeout = sendRequestContext.requestTimeout.getOrElse(300.seconds)
      tibrvResponse <- delay(Option(transport.sendRequest(tibrvMsg, timeout.toSeconds))).onError { case t =>
        error(s"failed to send message on $this context: $context", t)
      }
      tibrvResponse <- tibrvResponse match {
        case Some(tibrvResponse) =>
          pure(tibrvResponse)
        case None =>
          raiseError(RequestTimeout(timeout))
      }
      response <- Unmarshaller[Either[Throwable, RESP]].unmarshal(tibrvResponse.getAsBytes) match {
        case Right(r) =>
          r match {
            case Right(r) =>
              pure(r.asInstanceOf[RESPONSE])
            case Left(t) =>
              raiseError(t)
          }
        case Left(t) =>
          raiseError(t)
      }
      _ <- trace(s"sent request ${request.toString.take(200)} on $subject")
    } yield DefaultRequestResponseResult(
      DefaultMessageSendResult[F, REQUEST](unit, unit),
      DefaultMessageReceiveResult[F, RESPONSE](
        pure(response),
        Map(),
        None,
        unit,
        unit,
        pure(tibrvResponse),
        pure(transport),
        delay(tibrvResponse.getSendSubject),
        pure(now)
      )
    )

  def stop = delay(transport.destroy())
}

object TibrvRequestResponseClient extends CatsUtils with Logging {

  def apply[F[_] : Concurrent, REQ: Marshaller, RESP: Unmarshaller](
    subject: String
  )(
    implicit context: TibrvContext,
    responseUnmarshaller: Unmarshaller[Either[Throwable, RESP]]
  ): F[RequestResponseClient[F, REQ, RESP]] = delay(new TibrvRequestResponseClient[F, REQ, RESP](subject))
}