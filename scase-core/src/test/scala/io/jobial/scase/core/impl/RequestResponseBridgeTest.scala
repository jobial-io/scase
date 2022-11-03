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
package io.jobial.scase.core.impl

import cats.Eq
import cats.effect.IO
import io.jobial.scase.core._
import io.jobial.scase.core.impl.RequestResponseBridge.allowAllFilter
import io.jobial.scase.core.impl.RequestResponseBridge.fixedDestination
import io.jobial.scase.core.test.ServiceTestSupport
import io.jobial.scase.core.test.TestRequest
import io.jobial.scase.core.test.TestResponse
import io.jobial.scase.local.LocalServiceConfiguration.requestResponse
import io.jobial.scase.marshalling.serialization._

class RequestResponseBridgeTest
  extends ServiceTestSupport {

  def testRequestResponseBridge[REQ, RESP, REQUEST <: REQ, RESPONSE <: RESP : Eq](
    request: REQUEST,
    response: RESPONSE,
    source: RequestHandler[IO, REQ, RESP] => IO[Service[IO]],
    destination: Service[IO],
    sourceClient: Service[IO] => IO[RequestResponseClient[IO, REQ, RESP]],
    destClient: RequestResponseClient[IO, REQ, RESP]
  )(
    implicit mapping: RequestResponseMapping[REQ, RESP],
    mapping1: RequestResponseMapping[REQUEST, RESPONSE]
  ) =
    for {
      bridge <- RequestResponseBridge(source, fixedDestination(destClient), allowAllFilter[IO, REQ])
      h <- destination.start
      serviceState <- bridge.start
      client <- sourceClient(serviceState.asInstanceOf[RequestResponseBridgeServiceState[IO]].requestResponseService)
      r <- client ? request
    } yield assert(response === r)

  "request-response bridge" should "work" in {
    val sourceConfig = requestResponse[TestRequest[_ <: TestResponse], TestResponse]("source")
    val destinationConfig = requestResponse[TestRequest[_ <: TestResponse], TestResponse]("destination")
    for {
      (destService, destClient) <- destinationConfig.serviceAndClient(requestHandler)
      r <- testRequestResponseBridge(request1, response1, sourceConfig.service[IO], destService, { service =>
        sourceConfig.client(service.asInstanceOf[ConsumerProducerRequestResponseService[IO, TestRequest[_ <: TestResponse], TestResponse]])
      }, destClient)
    } yield r
  }
}

