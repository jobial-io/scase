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

import cats.effect.LiftIO
import cats.implicits._
import io.jobial.scase.aws.client.AwsContext
import io.jobial.scase.core.DefaultMessageReceiveResult
import io.jobial.scase.core.RequestResponseClient
import io.jobial.scase.core.RequestResponseMapping
import io.jobial.scase.core.RequestResponseResult
import io.jobial.scase.core.SendRequestContext
import io.jobial.scase.core.impl.CatsUtils
import io.jobial.scase.core.impl.ConcurrentEffect
import io.jobial.scase.core.impl.DefaultMessageSendResult
import io.jobial.scase.core.impl.DefaultRequestResponseResult
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshaller
import io.jobial.scase.marshalling.Unmarshaller
import java.nio.charset.StandardCharsets


case class LambdaRequestResponseClient[F[_] : ConcurrentEffect : LiftIO, REQ: Marshaller, RESP: Unmarshaller](
  functionName: String
)(
  implicit val awsContext: AwsContext
) extends RequestResponseClient[F, REQ, RESP] with CatsUtils with Logging {

  import awsContext.lambdaClient._

  override def sendRequestWithResponseMapping[REQUEST <: REQ, RESPONSE <: RESP](
    request: REQUEST,
    requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE]
  )(
    implicit sendRequestContext: SendRequestContext
  ): F[RequestResponseResult[F, REQUEST, RESPONSE]] =
    for {
      response <- liftIO(invoke(functionName, Marshaller[REQ].marshalToText(request))).onError { case t =>
        error(s"error invoking lambda function $functionName", t)
      }
      responsePayload = new String(response.getPayload.array, StandardCharsets.UTF_8)
      m <- Unmarshaller[RESP].unmarshalFromText(responsePayload) match {
        case Right(r) =>
          pure(r.asInstanceOf[RESPONSE])
        case Left(t) =>
          raiseError(t)
      }
    } yield
      DefaultRequestResponseResult(
        DefaultMessageSendResult[F, REQUEST](unit, unit),
        DefaultMessageReceiveResult[F, RESPONSE](
          pure(m),
          Map(), // TODO: propagate attributes here
          None,
          unit,
          unit,
          pure(responsePayload),
          pure(response)
        )
      )

  def stop = unit
}

