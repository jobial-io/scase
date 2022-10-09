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
import cats.effect.ContextShift
import io.jobial.scase.aws.client.AwsContext
import io.jobial.scase.core.ServiceConfiguration
import io.jobial.scase.core.impl.CatsUtils
import io.jobial.scase.logging.Logging
import io.jobial.scase.marshalling.Marshaller
import io.jobial.scase.marshalling.Unmarshaller

case class LambdaServiceConfiguration[REQ: Marshaller : Unmarshaller, RESP: Marshaller : Unmarshaller](
  functionName: String
) extends ServiceConfiguration with CatsUtils with Logging {

  // TODO: overload constructor for this
  lazy val serviceName = functionName

  def client[F[_] : Concurrent : ContextShift](implicit awsContext: AwsContext = AwsContext()) =
    delay(LambdaRequestResponseClient[F, REQ, RESP](functionName)(Concurrent[F], Marshaller[REQ], Unmarshaller[RESP], awsContext))

  lazy val requestMarshaller = Marshaller[REQ]

  lazy val requestUnmarshaller = Unmarshaller[REQ]

  lazy val responseMarshaller = Marshaller[RESP]

  lazy val responseUnmarshaller = Unmarshaller[RESP]
}


