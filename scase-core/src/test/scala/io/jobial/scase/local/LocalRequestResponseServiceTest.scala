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
import io.jobial.scase.core._

import scala.concurrent.TimeoutException
import scala.concurrent.duration.DurationInt

class LocalRequestResponseServiceTest
  extends RequestResponseTestSupport {
  
  "request-response service" should "reply successfully" in {
    for {
      t <- LocalRequestResponseServiceConfiguration[TestRequest[_ <: TestResponse], TestResponse]("hello").serviceAndClient(requestHandler)
      (service, client) = t
      _ <- service.start
      r1 <- client.sendRequest(request1)
      r1 <- r1.response
      r11 <- client ? request1
      r2 <- client.sendRequest(request2)
      r2 <- r2.response
      r21 <- client ? request2
    } yield assert(
      response1 === r1.message && response1 === r11 &&
        response2 === r2.message && response2 === r21
    )
  }

  "another request-response service" should "reply successfully" in {
    for {
      t <- LocalRequestResponseServiceConfiguration[Req, Resp]("hello").serviceAndClient(anotherRequestProcessor)
      (service, client) = t
      _ <- service.start
      r <- client.sendRequest(Req1())
      r <- r.response
      r1 <- client ? Req1()
    } yield assert(Resp1() == r.message && Resp1() == r1)
  }

  "request" should "time out if service is not started" in {
    implicit val sendRequestContext = SendRequestContext(requestTimeout = Some(1.second))

    recoverToSucceededIf[TimeoutException] {
      for {
        t <- localServiceAndClient("greeting", requestHandler)
        (_, client) = t
        _ <- client ? request1
      } yield succeed
    }
  }
}
