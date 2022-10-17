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
package io.jobial.scase.local

import cats.effect.IO
import cats.effect.concurrent.MVar
import io.jobial.scase.core._
import io.jobial.scase.core.impl.CatsUtils
import io.jobial.scase.core.test.Req1
import io.jobial.scase.core.test.Resp1
import io.jobial.scase.core.test.ServiceTestSupport
import io.jobial.scase.core.test.TestMessageHandler
import io.jobial.scase.core.test.TestRequest
import io.jobial.scase.core.test.TestResponse
import io.jobial.scase.local.LocalServiceConfiguration.handler
import io.jobial.scase.local.LocalServiceConfiguration.requestResponse
import scala.concurrent.duration.DurationInt


class LocalServiceTest
  extends ServiceTestSupport with CatsUtils {

  "request-response service" should "reply successfully" in {
    for {
      (service, client) <- requestResponse("hello").serviceAndClient(requestHandler)
      r <- testSuccessfulReply(service, client)
    } yield r
  }

  "another request-response service" should "reply successfully" in {
    for {
      (service, client) <- requestResponse("hello").serviceAndClient(anotherRequestProcessor)
      _ <- service.start
      r <- testSuccessfulReply(client, Req1(), Resp1())
    } yield r
  }

  "request" should "time out if service is not started" in {
    implicit val sendRequestContext = SendRequestContext(requestTimeout = Some(1.second))

    recoverToSucceededIf[RequestTimeout] {
      for {
        (_, client) <- localServiceAndClient("greeting", requestHandler)
        _ <- client ? request1
      } yield succeed
    }
  }

  "request-response service" should "succeed in load test" in {
    val serviceConfig = requestResponse[TestRequest[_ <: TestResponse], TestResponse]("hello")
    for {
      service <- serviceConfig.service(requestHandler)
      client <- serviceConfig.client(service)
      r <- testMultipleRequests(service, IO.pure(client), _ => request1, _ => response1)
    } yield r
  }

  "request-response service" should "succeed in load test with different clients" in {
    val serviceConfig = requestResponse[TestRequest[_ <: TestResponse], TestResponse]("hello")
    for {
      service <- serviceConfig.service(requestHandler)
      r <- testMultipleRequests(service, serviceConfig.client(service), _ => request1, _ => response1)
    } yield r
  }

  "message handler service" should "receive successfully" in {
    val serviceConfig = handler[TestRequest[_ <: TestResponse]](
      s"hello")

    for {
      receivedMessage <- MVar.empty[IO, TestRequest[_ <: TestResponse]]
      service <- serviceConfig.service(TestMessageHandler(receivedMessage))
      senderClient <- serviceConfig.client(service)
      r <- testSuccessfulMessageHandlerReceive(service, senderClient, receivedMessage)
    } yield r
  }

}
