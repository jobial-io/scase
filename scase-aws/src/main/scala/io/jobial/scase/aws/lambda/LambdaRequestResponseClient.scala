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

import cats.Monad
import cats.effect.{Concurrent, IO}
import cats.implicits._
import io.jobial.scase.aws.client.AwsContext
import io.jobial.scase.core.{DefaultMessageReceiveResult, MessageReceiveResult, RequestResponseClient, RequestResponseMapping, RequestResult, SendRequestContext}
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}

import java.nio.charset.StandardCharsets
import scala.concurrent.ExecutionContext


case class LambdaRequestResponseClient[F[_] : Concurrent, REQ: Marshaller, RESP: Unmarshaller](
  functionName: String
)(
  implicit val awsContext: AwsContext,
  ec: ExecutionContext
) extends RequestResponseClient[F, REQ, RESP] {

  import awsContext.lambdaClient._

  override def sendRequestWithResponseMapping[REQUEST <: REQ, RESPONSE <: RESP](
    request: REQUEST,
    requestResponseMapping: RequestResponseMapping[REQUEST, RESPONSE]
  )(
    implicit sendRequestContext: SendRequestContext
  ): F[RequestResult[F, RESPONSE]] = Monad[F].pure(LambdaRequestResult(
    Concurrent[F].liftIO(
      for {
        response <- invoke(functionName, Marshaller[REQ].marshalToText(request))
        r <- {
          Unmarshaller[RESP].unmarshalFromText(new String(response.getPayload.array, StandardCharsets.UTF_8)) match {
            case Right(r) =>
              IO(r.asInstanceOf[RESPONSE])
            case Left(t) =>
              IO.raiseError(t)
          }
        } recoverWith {
          case t =>
            IO.raiseError(t)
        }
      } yield r
    )
  ))
}

case class LambdaRequestResult[F[_], RESPONSE](resp: F[RESPONSE])(implicit m: Monad[F]) extends RequestResult[F, RESPONSE] {

  def response =
    for {
      resp <- resp
    } yield DefaultMessageReceiveResult[F, RESPONSE](
      resp,
      Map(), // TODO: propagate attributes here
      Monad[F].unit,
      Monad[F].unit
    )

  def commit = Monad[F].unit
}
