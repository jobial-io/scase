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
import io.jobial.scase.aws.client.AwsContext
import io.jobial.scase.core.ServiceConfiguration
import io.jobial.scase.marshalling.{Marshaller, Unmarshaller}

import scala.concurrent.ExecutionContext

case class LambdaRequestResponseServiceConfiguration[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
  functionName: String
) extends ServiceConfiguration {

  // TODO: overload constructor for this
  val serviceName = functionName

  def client[F[_] : Concurrent](implicit awsContext: AwsContext = AwsContext(), ec: ExecutionContext) =
    Concurrent[F].delay(LambdaRequestResponseClient[F, REQ, RESP](functionName))

  val requestMarshaller = Marshaller[REQ]
  
  val requestUnmarshaller = Unmarshaller[REQ]

  val responseMarshaller = Marshaller[RESP]

  val responseUnmarshaller = Unmarshaller[RESP]
}


